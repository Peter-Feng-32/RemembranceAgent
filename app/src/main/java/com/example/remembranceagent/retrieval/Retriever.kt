package com.example.remembranceagent.retrieval

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.queries.mlt.MoreLikeThis
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.store.FSDirectory
import java.io.StringReader


private val TAG = "Retriever"
// Todo: Make indexPath something that the user sets.  Fail gracefully if it can't be accessed.
// Todo: Represent documents with interfaces and classes.
class Retriever {
    constructor() {

    }
    val index: FSDirectory = FSDirectory.open(indexPath)
    val analyzer: StandardAnalyzer = StandardAnalyzer()

    fun query(queryString: String): ScoreDoc? {
        val queryParser = StandardQueryParser(analyzer)
        val query = queryParser.parse(queryString, "Content")

        val hitsPerPage = 1
        val reader: IndexReader = DirectoryReader.open(index)
        val searcher = IndexSearcher(reader)

        val moreLikeThis = MoreLikeThis(reader)
        moreLikeThis.fieldNames = arrayOf("content") // The field(s) to use for similarity
        moreLikeThis.minTermFreq = 1 // Minimum term frequency
        moreLikeThis.minDocFreq = 1 // Minimum document frequency
        val paragraph = "This is the paragraph text you want to find similar documents for."
        val topDocs = searcher.search(moreLikeThis.like(StringReader(paragraph), "content"), 1)

        // Iterate over topDocs to get the similar documents
        for (i in topDocs.scoreDocs.indices) {
            println("Doc ID: " + topDocs.scoreDocs[i].doc + " Score: " + topDocs.scoreDocs[i].score)
        }

        val docs = searcher.search(query, hitsPerPage)
        val hit = docs.scoreDocs.get(0)
        return hit
    }

}