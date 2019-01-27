package com.github.sybila;

import com.github.sybila.checker.SequentialChecker;
import com.github.sybila.checker.Solver;
import com.github.sybila.checker.StateMap;
import com.github.sybila.huctl.Formula;
import com.github.sybila.huctl.HUCTLParser;
import com.github.sybila.ode.generator.NodeEncoder;
import com.github.sybila.ode.generator.rect.Rectangle;
import com.github.sybila.ode.generator.rect.RectangleOdeModel;
import com.github.sybila.ode.generator.rect.RectangleSolver;
import com.github.sybila.ode.model.OdeModel;
import com.github.sybila.ode.model.Parser;

import java.io.File;
import java.util.Set;

public class Test {

    public static void main(String[] args) {
        // provides parameter set operations
        Solver<Set<Rectangle>> solver = new RectangleSolver(new Rectangle(new double[] { -1.0, 1.0, -1.0, 1.0 }));

        // state space/transition generator for one continuous mode
        Parser odeParser = new Parser();
        OdeModel mode1 = odeParser.parse(new File("Some/path/to/some/model.bio"));
        RectangleOdeModel generator = new RectangleOdeModel(mode1, true);

        // encoding of state coordinates into integers
        NodeEncoder encoder = new NodeEncoder(mode1);
        int[] coords = encoder.decodeNode(10);
        int state = encoder.encodeVertex(coords);

        HeaterHybridModel hybrid = new HeaterHybridModel(solver);

        SequentialChecker<Set<Rectangle>> checker = new SequentialChecker<>(hybrid);

        HUCTLParser formulaParser = new HUCTLParser();
        Formula prop = formulaParser.formula("EF x > 5");
        StateMap<Set<Rectangle>> result = checker.verify(prop);

        for (int i=0; i<generator.getStateCount(); i++) {
            System.out.println("For state "+i+" property holds for parameters "+result.get(i));
        }
    }

}
