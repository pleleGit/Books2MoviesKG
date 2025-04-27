# BooksMoviesKG

**A knowledge graph connecting books and their movie adaptations using RML mapping, RDF4J, and GraphDB.**

---

## 📚 Project Overview

This project transforms two datasets — one about books and another about movies — into a connected RDF knowledge graph. Using RML mappings, the datasets are semantically enriched and stored in a GraphDB repository, enabling complex SPARQL queries for exploration.

---

## 🛠 Technologies Used

- **Java**
- **Maven**
- **RML Mapper (Java)**
- **RDF4J Framework**
- **GraphDB**
- **SPARQL**

---

## 📂 Project Structure

| Folder/File | Description |
|:------------|:------------|
| `src/main/java/` | Java source code (Main.java, helpers) |
| `src/main/resources/` | RML mappings and ontology files |
| `src/main/resources/` | Input CSV datasets: books and movies |
| `outputFile.ttl` | Generated RDF triples (Turtle syntax) |
| `outputFileFinal.ttl` | Generated RDF triples. Linked books to movies. (Turtle syntax) |

---

## 🚀 How to Run the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/BooksMoviesKG.git
   cd BooksMoviesKG
   ```

2. **Set up the environment:**
   - Install **Java 17**.
   - Install **GraphDB** and start the server (default at `http://localhost:7200`).

3. **Create a GraphDB repository:**
   - Name: `MiniProject`
   - Repository type: `owlim:MemoryStore`

4. **Build and run the Java application:**
   ```bash
   mvn clean install
   mvn exec:java -Dexec.mainClass="org.example.miniproject.Main"
   ```

5. **View the RDF data:**
   - Access GraphDB Workbench at `http://localhost:7200`
   - Explore your RDF triples and run SPARQL queries!

---


**From Books to Movies — Linked, Semantically, in GraphDB.** 📚🎬


