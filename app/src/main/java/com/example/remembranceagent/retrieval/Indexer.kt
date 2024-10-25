package com.example.remembranceagent.retrieval

import android.util.Log
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path

private val TAG = "Indexer"

class Indexer(val indexPath: Path, val documentsPath: Path) {
    private var indexWriter : IndexWriter;

    init {
        setupDirs()
        val analyzer = EnglishAnalyzer()
        val index = FSDirectory.open(indexPath)
        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriter = IndexWriter(index, indexWriterConfig)
    }

    fun setupDirs() {
        if (Files.notExists(TEMP_INDEX_PATH)) {
            try {
                Files.createDirectories(TEMP_INDEX_PATH)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (Files.notExists(indexPath)) {
            try {
                Files.createDirectories(indexPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (Files.notExists(documentsPath)) {
            try {
                Files.createDirectories(documentsPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun indexDocuments() {
        indexTxtDocuments(documentsPath)
        // Todo: Index other types of documents
    }

    private fun indexTxtDocuments(documentsPath: Path) {
        Log.w(TAG, "Indexing txt documents")
        Files.newDirectoryStream(documentsPath).use { stream: DirectoryStream<Path> ->
            for (file in stream) {
                if(Files.isDirectory(file)) {
                    Log.w(TAG, "is directory: " )
                    indexTxtDocuments(file)
                }
                else if (file.fileName.toString().endsWith(".txt")) {
                    // Get the title (file name without extension)
                    val title = file.fileName.toString().removeSuffix(".txt")
                    Log.w(TAG, "Indexing document with title " + title)

                    // Read the content of the file
                    val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    // Get the file path
                    val filePath = file.toAbsolutePath().toString()

                    val document = createTxtDocument(title, content, filePath)
                    indexWriter.addDocument(document)
                }

            }
        }
    }

    // If we want to retrieve content we can read the original file.
    private fun createTxtDocument(title: String, content: String, filePath: String) : Document{
        val document = Document()
        document.add(TextField("Title", title, Field.Store.YES))
        document.add(TextField("Content", content, Field.Store.YES))
        document.add(TextField("FilePath", filePath, Field.Store.YES))
        return document
    }

    fun close() {
        indexWriter.close()
    }
}