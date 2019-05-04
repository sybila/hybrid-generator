package com.github.sybila

import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import java.nio.file.Path


/**
 * Utility function for generating all ODE models for one hybrid model at once.
 * @param dataPath path to .bio file containing data which have all ODE models in common
 * @param equationPaths paths to .bio files where is data specific for an ODE model
 */
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
