package com.github.sybila

import com.github.sybila.checker.Solver
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import java.lang.UnsupportedOperationException
import java.nio.file.Path

class HybridModelBuilder() {
    private val modes: MutableList<HybridMode> = mutableListOf()
    private val transitions: MutableList<HybridTransition> = mutableListOf()
    private var solver: Solver<MutableSet<Rectangle>>? = null

    var variables: List<OdeModel.Variable> = emptyList()
    var parameters: List<OdeModel.Parameter> = emptyList()


    fun withMode(label: String, pathToOde: Path): HybridModelBuilder {
        val odeModel = Parser().parse(pathToOde.toFile())
        val mode = HybridMode(label, odeModel, EmptyHybridCondition())
        modes.add(mode)

        initParamsAndVars()
        return this
    }

    fun withModeWithConstantInvariant(label: String, pathToOde: Path, conditionVarName: String, conditionConstant: Double, gt: Boolean): HybridModelBuilder {
        val odeModel = Parser().parse(pathToOde.toFile())
        val conditionVariable = odeModel.variables.first{it.name == conditionVarName}
        val variableOrder = odeModel.variables.map { it.name }.toTypedArray()
        val mode = HybridMode(label, odeModel, ConstantHybridCondition(conditionVariable, conditionConstant, gt, variableOrder))
        modes.add(mode)

        initParamsAndVars()
        return this
    }


    fun withModeWithConjunctionInvariant(label: String, pathToOde: Path, invariants: List<Triple<String, Double, Boolean>>): HybridModelBuilder {
        val odeModel = Parser().parse(pathToOde.toFile())
        val variableOrder = odeModel.variables.map { it.name }.toTypedArray()
        val conditions = mutableListOf<ConstantHybridCondition>()

        for (i in invariants) {
            val conditionVariable = odeModel.variables.first{it.name == i.first}
            conditions.add(ConstantHybridCondition(conditionVariable, i.second, i.third, variableOrder))
        }
        val mode = HybridMode(label, odeModel, ConjunctionHybridCondition(conditions))
        modes.add(mode)

        initParamsAndVars()
        return this
    }


    fun withModes(labels: List<String>, pathToData: Path, pathToOdes: List<Path>) : HybridModelBuilder {
        if (labels.isEmpty())
            throw IllegalArgumentException("No data to build")

        if (labels.size != pathToOdes.size)
            throw IllegalArgumentException("Incompatible number of labels and ode models used for mode generation")

        val odeModels =  generateOdeModels(pathToData, pathToOdes)
        for (i in 0 until labels.size) {
            modes.add(HybridMode(labels[i], odeModels[i], EmptyHybridCondition()))
        }

        initParamsAndVars()
        return this
    }


    fun withModesWithConstantInvariants(labels: List<String>, pathToData: Path, pathToOdes: List<Path>, invariants: List<Triple<String, Double, Boolean>>) : HybridModelBuilder {
        if (labels.isEmpty())
            throw IllegalArgumentException("No data to build")

        if (labels.size != pathToOdes.size)
            throw IllegalArgumentException("Incompatible number of labels and ode models used for mode generation")

        val odeModels =  generateOdeModels(pathToData, pathToOdes)
        val variableOrder = odeModels[0].variables.map { it.name }.toTypedArray()

        for (i in 0 until labels.size) {
            val variable = odeModels[0].variables.first { it.name == invariants[i].first }
            modes.add(HybridMode(labels[i], odeModels[i], ConstantHybridCondition(variable, invariants[i].second, invariants[i].third, variableOrder)))
        }

        initParamsAndVars()
        return this
    }


    fun withModesWithConjunctionInvariants(labels: List<String>, pathToData: Path, pathToOdes: List<Path>, invariants: List<List<Triple<String, Double, Boolean>>>) : HybridModelBuilder {
        if (labels.isEmpty())
            throw IllegalArgumentException("No data to build")

        if (labels.size != pathToOdes.size)
            throw IllegalArgumentException("Incompatible number of labels and ode models used for mode generation")

        val odeModels =  generateOdeModels(pathToData, pathToOdes)
        val variableOrder = odeModels[0].variables.map { it.name }.toTypedArray()

        for (i in 0 until labels.size) {
            val conditions = mutableListOf<ConstantHybridCondition>()
            for (inv in invariants[i]) {
                val conditionVariable = odeModels[0].variables.first{it.name == inv.first}
                conditions.add(ConstantHybridCondition(conditionVariable, inv.second, inv.third, variableOrder))
            }
            modes.add(HybridMode(labels[i], odeModels[i], ConjunctionHybridCondition(conditions)))
        }

        initParamsAndVars()
        return this
    }


    fun withTransition(from: String, to: String, resets: Map<String, Double> = emptyMap()) : HybridModelBuilder {
        transitions.add(HybridTransition(from, to, EmptyHybridCondition(), resets, variables))
        return this
    }


    fun withTransitionWithConstantCondition(from: String, to: String, conditionVarName: String, conditionConstant: Double, gt: Boolean, resets: Map<String, Double> = emptyMap()) : HybridModelBuilder {
        val conditionVariable = variables.first{it.name == conditionVarName}
        val variableOrder = variables.map { it.name }.toTypedArray()
        val condition = ConstantHybridCondition(conditionVariable, conditionConstant, gt, variableOrder)
        transitions.add(HybridTransition(from, to, condition, resets, variables))
        return this
    }


    fun withTransitionWithConjunctionCondition(from: String, to: String, conditions: List<Triple<String, Double, Boolean>>, resets: Map<String, Double> = emptyMap()) : HybridModelBuilder {
        val variableOrder = variables.map { it.name }.toTypedArray()
        val constantConditions = mutableListOf<ConstantHybridCondition>()

        for (i in conditions) {
            val conditionVariable = variables.first{it.name == i.first}
            constantConditions.add(ConstantHybridCondition(conditionVariable, i.second, i.third, variableOrder))
        }
        transitions.add(HybridTransition(from, to, ConjunctionHybridCondition(constantConditions), resets, variables))
        return this
    }


    fun withTransitionWithParametrizedCondition(from: String, to: String, conditionVarName: String, conditionParamName:String, gt: Boolean, resets: Map<String, Double> = emptyMap()) : HybridModelBuilder {
        val conditionVariable = variables.first{it.name == conditionVarName}
        val conditionParameter = parameters.first{it.name == conditionParamName}
        val condition = ParameterHybridCondition(conditionVariable, conditionParameter, gt)
        transitions.add(HybridTransition(from, to, condition, resets, variables))
        return this
    }

    fun withSolver(solver: Solver<MutableSet<Rectangle>>): HybridModelBuilder {
        this.solver = solver
        return this
    }

    fun build(): HybridModel {
        if (solver == null)
            throw UnsupportedOperationException("Can't build HybridModel without solver")
        if (modes.isEmpty())
            throw UnsupportedOperationException("Can't build HybridModel without modes")

        return HybridModel(solver!!, modes, transitions)
    }


    private fun initParamsAndVars() {
        val mode = modes.first()
        if (variables.isEmpty()) {
            variables = mode.odeModel.variables
        }
        if (parameters.isEmpty()) {
            parameters = mode.odeModel.parameters
        }
    }
}