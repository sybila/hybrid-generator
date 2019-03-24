package com.github.sybila

import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import java.nio.file.Path

fun generateOdeModels(dataPath: Path, equationPaths: List<Path>) : List<OdeModel> {
    val models = mutableListOf<OdeModel>()
    val dataFileContent: String
    with (dataPath.toFile()) {
        dataFileContent = this.readText() + System.lineSeparator()
    }

    for (equationPath in equationPaths) {
        with (equationPath.toFile()) {
            val equationFileContent = this.readText()
            val fullModelContent = dataFileContent + equationFileContent
            val model =  Parser().parse(fullModelContent)
            models.add(model)
        }
    }

    return models
}
