package com.example.remembranceagent.retrieval

import android.os.Environment
import java.io.File
import java.nio.file.Path

// Todo: Make indexPath something that the user sets.  Fail gracefully if it can't be accessed.
// Todo: Use multiple versions of the indexer so running the indexer in the background doesn't mess up the current index.
val indexPath: Path = File(Environment.getExternalStorageDirectory().path, "indexer_test").toPath()
val documentsPath: Path = File(Environment.getExternalStorageDirectory().path, "documents_test").toPath()