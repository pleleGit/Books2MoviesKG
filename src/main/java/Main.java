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
            // Step 3: Upload Ontology + Data to GraphDB
            pipeline.uploadToGraphDB(model);
            // Step 4: Run a SPARQL query
            pipeline.runSPARQLQuery();
            System.out.println("Pipeline completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Model runRMLMapping() throws Exception {
        System.out.println("Running RML Mapper...");
        System.out.println("Current working directory: " + System.getProperty("user.dir"));

        // Paths to both mapping files
        File bookMappingFile = new File("./src/main/resources/books/bookMappings.ttl");
        File movieMappingFile = new File("./src/main/resources/movies/movie_mapping.ttl");

        // Execute and collect both mappings
        Model bookModel = executeMapping(bookMappingFile);
        Model movieModel = executeMapping(movieMappingFile);

        // Merge models
        bookModel.addAll(movieModel);

        // Write combined output
        Writer output = new FileWriter("./src/main/resources/outputFile.ttl");
        Rio.write(bookModel, output, RDFFormat.TURTLE);
        output.close();

        return bookModel;
    }

    private Model executeMapping(File mappingFile) throws Exception {
        System.out.println("Executing mapping: " + mappingFile.getName());

        try (InputStream mappingStream = new FileInputStream(mappingFile)) {
            QuadStore rmlStore = QuadStoreFactory.read(mappingStream);
            RecordsFactory factory = new RecordsFactory(mappingFile.getParent());

            Map<String, Class> libraryMap = new HashMap<>();
            libraryMap.put("IDLabFunctions", IDLabFunctions.class);
            FunctionLoader functionLoader = new FunctionLoader(null, libraryMap);

            QuadStore outputStore = new RDF4JStore();
            Executor executor = new Executor(rmlStore, factory, functionLoader, outputStore, Utils.getBaseDirectiveTurtle(mappingStream));
            QuadStore result = executor.executeV5(null).get(new NamedNode("rmlmapper://default.store"));
            return ((RDF4JStore) result).getModel();
        }
    }

private void processModel(@NotNull Model model) {
    System.out.println("Post-processing model (linking adaptations)...");
    ValueFactory vf = SimpleValueFactory.getInstance();
    IRI dcTitle = vf.createIRI("http://purl.org/dc/terms/title");
    IRI camoTitle = vf.createIRI("http://www.semanticweb.org/administrator/ontologies/2017/2/Camo_Ontology#title");
    Map<String, IRI> books = new HashMap<>();
    Map<String, IRI> movies = new HashMap<>();
    // collect all books titles
    model.filter(null, dcTitle, null).forEach(stmt -> {
        String title = stmt.getObject().stringValue().toLowerCase().trim();
        IRI subject = (IRI) stmt.getSubject();
        books.put(title, subject);
    });
    // Collect all movie titles
    model.filter(null, camoTitle, null).forEach(stmt -> {
        String title = stmt.getObject().stringValue().toLowerCase().trim();
        IRI subject = (IRI) stmt.getSubject();
        movies.put(title, subject);
    });
    // Match by title and add adaptedInto / adaptationOf links
    for (Map.Entry<String, IRI> bookEntry : books.entrySet()) {
        String bookTitle = bookEntry.getKey();
        IRI bookIRI = bookEntry.getValue();
        for (Map.Entry<String, IRI> movieEntry : movies.entrySet()) {
            String movieTitle = movieEntry.getKey();
            IRI movieIRI = movieEntry.getValue();
            if (movieTitle.startsWith(bookTitle) || bookTitle.startsWith(movieTitle)) {
                model.add(bookIRI, vf.createIRI("http://example.org/mini#adaptedInto"), movieIRI);
                model.add(movieIRI, vf.createIRI("http://example.org/mini#adaptationOf"), bookIRI);
                System.out.println("Linking: Book \"" + bookTitle + "\" -> Movie \"" + movieTitle + "\"");
                break;
            }
        }
    }
    try (Writer output = new FileWriter("./src/main/resources/outputFileFinal.ttl")) {
        Rio.write(model, output, RDFFormat.TURTLE);
        System.out.println("Post-processed model saved to outputFileFinal.ttl");
    } catch (IOException e) {
        e.printStackTrace();
    }
}

    private void uploadToGraphDB(Model model) {
        System.out.println("Uploading ontology and data to GraphDB...\n" + SERVER_URL + "/repositories/" + REPOSITORY_ID);
        HTTPRepository repo = new HTTPRepository(SERVER_URL + "/repositories/" + REPOSITORY_ID);
        repo.initialize();
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.clear();
            conn.begin();
            try {
                conn.add(new FileInputStream("./src/main/resources/books/bookOntology.owl"),
                        "urn:base",
                        RDFFormat.RDFXML);
                conn.add(new FileInputStream("./src/main/resources/movies/CAMO_Ontology.owl"),
                        "urn:base",
                        RDFFormat.RDFXML);
                conn.add(new FileInputStream("./src/main/resources/outputFileFinal.ttl"),
                        "urn:base",
                        RDFFormat.TURTLE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            conn.add(model);
            conn.commit();
        }
        repo.shutDown();
    }

    private void runSPARQLQuery() {
        System.out.println("Running SPARQL query...");
        HTTPRepository repo = new HTTPRepository(SERVER_URL + "/repositories/" + REPOSITORY_ID);
        repo.initialize();
        String sparql = """
            PREFIX ex: <http://example.org/mini#>
            PREFIX dc: <http://purl.org/dc/terms/>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            SELECT ?book ?bookTitle ?bookRating ?publishedYear ?author
            WHERE {
              ?book a ex:Book ;
                    dc:title ?bookTitle ;
                    ex:ratingValue ?bookRating ;
                    ex:publishedYear ?publishedYear ;
                    ex:hasAuthor ?author .
              FILTER(CONTAINS(LCASE(str(?author)), "tolkien") && ?bookRating > 4.4)
            }
            ORDER BY ?bookTitle
        """;
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.prepareTupleQuery(sparql).evaluate().forEach(bindingSet -> {
                String title = bindingSet.getValue("bookTitle") != null ? bindingSet.getValue("bookTitle").stringValue() : "N/A";
                String rating = bindingSet.getValue("bookRating") != null ? bindingSet.getValue("bookRating").stringValue() : "N/A";
                String year = bindingSet.getValue("publishedYear") != null ? bindingSet.getValue("publishedYear").stringValue() : "N/A";
                String author = bindingSet.getValue("author") != null ? bindingSet.getValue("author").stringValue() : "N/A";
                System.out.println("Title: " + title + " | Rating: " + rating + " | Year: " + year + " | Author: " + author);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        repo.shutDown();
    }
}
