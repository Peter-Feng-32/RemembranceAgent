package com.example.remembranceagent.retrieval

import android.os.Environment
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path

private val TAG = "Indexer"

class Indexer {
    private val indexWriter : IndexWriter;
    constructor() {
        val analyzer = StandardAnalyzer()
        val index = FSDirectory.open(indexPath)

        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriter = IndexWriter(index, indexWriterConfig)
    }

    fun indexDocuments() {
        indexTxtDocuments()
        // Todo: Index other types of documents
    }

    private fun indexTxtDocuments() {
        Files.newDirectoryStream(documentsPath, "*.txt").use { stream: DirectoryStream<Path> ->
            for (file in stream) {
                // Get the title (file name without extension)
                val title = file.fileName.toString().removeSuffix(".txt")
                // Read the content of the file
                val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                // Get the file path
                val filePath = file.toAbsolutePath().toString()

                val document = createTxtDocument(title, content, filePath)
                indexWriter.addDocument(document)
            }
        }
    }

    // If we want to retrieve content we can read the original file.
    private fun createTxtDocument(title: String, content: String, filePath: String) : Document{
        val document = Document()
        document.add(TextField("Title", title, Field.Store.YES))
        document.add(TextField("Content", content, Field.Store.NO))
        document.add(TextField("FilePath", filePath, Field.Store.YES))
        return document
    }

    fun close() {
        indexWriter.close()
    }
}