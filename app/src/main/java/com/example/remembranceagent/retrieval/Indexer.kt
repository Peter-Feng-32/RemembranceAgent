package com.example.remembranceagent.retrieval

import android.content.SharedPreferences
import android.util.Log
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.TextField
import org.apache.lucene.index.FieldInfo.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


private val TAG = "Indexer"

class Indexer(val preferences: SharedPreferences, val documentsPath: Path) {
    companion object {
        private val listeners = mutableListOf<() -> Unit>()
        var lock = Object()

        fun addListener(listener: () -> Unit) {
            listeners.add(listener)
        }

        private fun notifyListeners() {
            for (listener in listeners) {
                listener()
            }
        }

        fun removeListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        var typeOffsets = FieldType(TextField.TYPE_STORED)
    }


    val analyzer = EnglishAnalyzer(Version.LUCENE_47)

    init {
        typeOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        setupDirs()
    }

    fun setupDirs() {
        if (Files.notExists(INDICES_PATH)) {
            try {
                Files.createDirectories(INDICES_PATH)
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

    fun getLatestIndexName(): String{
        val indicesDir = FSDirectory.open(INDICES_PATH.toFile())
        val indices: List<String> =
            indicesDir.directory.listFiles { it: File -> it.isDirectory }?.map { it.name } ?: emptyList()
        var versionNum = 0
        val latestIndexDirName = indices.lastOrNull()
        if (latestIndexDirName != null) {
            val latestVersionNum: Int? = (latestIndexDirName.split("_").getOrNull(1))?.toIntOrNull()
            if (latestVersionNum != null) {
                versionNum = latestVersionNum + 1
            }
        }
        return "${INDEX_NAME}_${versionNum}"
    }

    fun indexDocuments() {
        synchronized(lock) {
            val latestIndexName = getLatestIndexName()
            val newIndexDirPath = Paths.get(INDICES_PATH.toString(), latestIndexName)
            val newIndexDir = FSDirectory.open(newIndexDirPath.toFile())

            val indexWriterConfig = IndexWriterConfig(Version.LUCENE_47, analyzer)
            var indexWriter: IndexWriter? = null
            try {
                indexWriter = IndexWriter(newIndexDir, indexWriterConfig)
                indexTxtDocuments(documentsPath, indexWriter)
                indexWriter.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                indexWriter?.close()
            }

            // Todo: Index other types of documents

            // Cleanup: Change latest index path.
            preferences.edit().putString(INDEX_PATH_STRING_KEY, newIndexDirPath.toString()).commit()
            notifyListeners()
        }

    }

    private fun indexTxtDocuments(documentsPath: Path, indexWriter: IndexWriter) {
        Log.w(TAG, "Indexing txt documents")
        Files.newDirectoryStream(documentsPath).use { stream: DirectoryStream<Path> ->
            for (file in stream) {
                if(Files.isDirectory(file)) {
                    Log.w(TAG, "is directory: " )
                    indexTxtDocuments(file, indexWriter)
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
        document.add(Field("Content", content, typeOffsets))
        document.add(TextField("FilePath", filePath, Field.Store.YES))
        return document
    }
}