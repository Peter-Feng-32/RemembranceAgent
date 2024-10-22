package com.example.remembranceagent.retrieval

import android.util.Log
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.queries.mlt.MoreLikeThis
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.store.FSDirectory
import java.io.StringReader
import kotlin.io.path.Path


private val TAG = "Retriever"
// Todo: Make indexPath something that the user sets.  Fail gracefully if it can't be accessed.
// Todo: Represent documents with interfaces and classes.
class Retriever {
    lateinit var indexPath: String
    constructor(indexPath: String) {
        this.indexPath = indexPath
    }
    val index: FSDirectory = FSDirectory.open(Path(this.indexPath))

    fun query(queryString: String): Document? {
        val reader: IndexReader = DirectoryReader.open(index)
        val searcher = IndexSearcher(reader)

        val moreLikeThis = MoreLikeThis(reader)
        moreLikeThis.fieldNames = arrayOf("content") // The field(s) to use for similarity
        moreLikeThis.minTermFreq = 1 // Minimum term frequency
        moreLikeThis.minDocFreq = 1 // Minimum document frequency
        val topDocs = searcher.search(moreLikeThis.like(StringReader(queryString), "content"), 1)

        // Iterate over topDocs to get the similar documents
        for (i in topDocs.scoreDocs.indices) {
            Log.w(TAG, "Doc ID: " + topDocs.scoreDocs[i].doc + " Score: " + topDocs.scoreDocs[i].score)
        }
        return if (topDocs.scoreDocs.isNotEmpty()) {
            searcher.doc(topDocs.scoreDocs[0].doc)
        } else {
            null
        }

    }

}