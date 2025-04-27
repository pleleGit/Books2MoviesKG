import be.ugent.rml.Executor;
import be.ugent.rml.Utils;
import be.ugent.rml.functions.FunctionLoader;
import be.ugent.rml.functions.lib.IDLabFunctions;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.store.RDF4JStore;
import be.ugent.rml.term.NamedNode;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String SERVER_URL = "http://localhost:7200";
    private static final String REPOSITORY_ID = "MiniProject";

    public static void main(String[] args) {
        try {
            Main pipeline = new Main();
            // Step 1: Execute RML Mapping
            Model model = pipeline.runRMLMapping();
            // Step 2: Post-processing: split genres and link adaptations
            pipeline.processModel(model);
//            // Step 3: Upload Ontology + Data to GraphDB
//            pipeline.uploadToGraphDB(model);
            System.out.println("Pipeline completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Model runRMLMapping() throws Exception {
        System.out.println("Running RML Mapper...");
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        // Get the mapping string stream
        File mappingFile = new File("./src/main/resources/mappings.ttl");
        InputStream mappingStream = new FileInputStream(mappingFile);
        // Load mapping
        QuadStore rmlStore = QuadStoreFactory.read(mappingStream);
        RecordsFactory factory = new RecordsFactory(mappingFile.getParent());
        // Set up functions
        Map<String, Class> libraryMap = new HashMap<>();
        libraryMap.put("IDLabFunctions", IDLabFunctions.class);
        FunctionLoader functionLoader = new FunctionLoader(null, libraryMap);
        // Output
        QuadStore outputStore = new RDF4JStore();
        Executor executor = new Executor(rmlStore, factory, functionLoader, outputStore, Utils.getBaseDirectiveTurtle(mappingStream));
        QuadStore result = executor.executeV5(null).get(new NamedNode("rmlmapper://default.store"));
        Model rdf4jModel = ((RDF4JStore) result).getModel();
        // Output the results in a file
        Writer output = new FileWriter("./src/main/resources/outputFile.ttl");
        result.write(output, "turtle");
        output.close();
        return rdf4jModel;
    }

    private void processModel(@NotNull Model model) {
        System.out.println("Post-processing model (splitting genres, linking adaptations)...");
        ValueFactory vf = SimpleValueFactory.getInstance();
        Map<String, IRI> books = new HashMap<>();
        Map<String, IRI> movies = new HashMap<>();
        // collect all movies and books titles
        model.filter(null, vf.createIRI("http://purl.org/dc/terms/title"), null)
                .forEach(stmt -> {
                    String title = stmt.getObject().stringValue();
                    IRI subject = (IRI) stmt.getSubject();
                    if (subject.toString().contains("/book/")) {
                        books.put(title, subject);
                    } else if (subject.toString().contains("/movie/")) {
                        movies.put(title, subject);
                    }
                });
        // split genres
        List<Statement> genreStatements = new ArrayList<>();
        model.filter(null, vf.createIRI("http://example.org/mini#hasGenre"), null)
                .forEach(genreStatements::add); // First collect to a list
        for (org.eclipse.rdf4j.model.Statement stmt : genreStatements) {
            String[] genres = stmt.getObject().stringValue().split(",");
            IRI subject = (IRI) stmt.getSubject();
            model.remove(stmt); // Now safe to remove

            for (String genre : genres) {
                String clean = genre.trim().replaceAll(" ", "_");
                IRI genreIRI = vf.createIRI("http://example.org/mini/genre/" + clean);
                model.add(genreIRI, vf.createIRI("http://www.w3.org/2000/01/rdf-schema#label"), vf.createLiteral(genre.trim()));
                model.add(subject, vf.createIRI("http://example.org/mini#hasGenre"), genreIRI);
            }
        }
        // add the property adaptedInto (book->movie) and adaptationOf (movie->book) between books and movies
        for (Map.Entry<String, IRI> entry : books.entrySet()) {
            String bookTitle = entry.getKey();
            IRI bookIRI = entry.getValue();
            movies.forEach((movieTitle, movieIRI) -> {
                if (movieTitle.startsWith(bookTitle.split("\\(")[0].trim())) {
                    model.add(bookIRI, vf.createIRI("http://example.org/mini#adaptedInto"), movieIRI);
                    model.add(movieIRI, vf.createIRI("http://example.org/mini#adaptationOf"), bookIRI);
                    System.out.println("Adding connection between " + bookIRI + " and " + movieIRI);
                }
            });
        }
        // Write final output to Turtle file
        try (Writer output = new FileWriter("./src/main/resources/outputFileFinal.ttl")) {
            Rio.write(model, output, RDFFormat.TURTLE);
            System.out.println("Post-processed model saved to outputFile.ttl");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadToGraphDB(Model model) {
        System.out.println("Uploading ontology and data to GraphDB...");
        HTTPRepository repo = new HTTPRepository(SERVER_URL + "/repositories/" + REPOSITORY_ID);
        repo.initialize();
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.clear();
            // Load ontology
            conn.begin();
            try {
                conn.add(Main.class.getResourceAsStream("/mappings/ontology.ttl"), "urn:base", RDFFormat.TURTLE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            conn.add(model);
            conn.commit();
        }
        repo.shutDown();
    }
}
