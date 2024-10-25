package com.example.remembranceagent.retrieval

import android.util.Log
import org.apache.lucene.analysis.en.EnglishAnalyzer
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
class Retriever(val indexPath: String) {
    val index: FSDirectory = FSDirectory.open(Path(this.indexPath))

    fun query(queryString: String): RetrievedResult? {
        val reader: IndexReader = DirectoryReader.open(index)
        val searcher = IndexSearcher(reader)

        val moreLikeThis = MoreLikeThis(reader)
        moreLikeThis.analyzer = EnglishAnalyzer()
        moreLikeThis.fieldNames = arrayOf("Content") // The field(s) to use for similarity
        moreLikeThis.minTermFreq = 0 // Minimum term frequency
        moreLikeThis.minDocFreq = 1 // Minimum document frequency

        val topDocs = searcher.search(moreLikeThis.like(StringReader(queryString), "Content"), 1)

        // Iterate over topDocs to get the similar documents
        for (i in topDocs.scoreDocs.indices) {
            Log.w(TAG, "Doc ID: " + topDocs.scoreDocs[i].doc + " Score: " + topDocs.scoreDocs[i].score)
        }
        return if (topDocs.scoreDocs.isNotEmpty()) {
            RetrievedResult(title = searcher.doc(topDocs.scoreDocs[0].doc).get("Title"), score=topDocs.scoreDocs[0].score)
        } else {
            null
        }

    }

}

data class RetrievedResult(val title: String, val score: Float) {
}