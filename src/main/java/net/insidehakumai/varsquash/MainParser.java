package net.insidehakumai.varsquash;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * JavaMethodParserのエントリーポイント用クラス
 */
public class MainParser {

    public static void main(String[] args) {
        Options options = new Options();

        Option trainDirOpt = new Option("i", "infile", true, "path of file to be deranged");
        trainDirOpt.setRequired(true);
        options.addOption(trainDirOpt);

        Option outputDirOpt = new Option("o", "outfile", true, "path to result file"); // TODO ちゃんと書く
        outputDirOpt.setRequired(true);
        options.addOption(outputDirOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("options", options);

            System.exit(1);
            return;
        }

        File inputFile = new File(cmd.getOptionValue("infile"));
        CompilationUnit cu;
        try {
            TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
            reflectionTypeSolver.setParent(reflectionTypeSolver);

            CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
            combinedSolver.add(reflectionTypeSolver);

            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
            JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);

            cu = JavaParser.parse(inputFile);
        } catch (FileNotFoundException e) {
            cu = null;
            System.err.println(String.format("ERROR: No such file: %s", inputFile.getPath()));
            System.exit(1);
        }

        cu.findAll(MethodDeclaration.class).forEach(methodDec -> {
            SquashPatternManager squashPatternManager = new SquashPatternManager();
            // outputASTRecursively(0, methodDec);
            System.out.println(String.format("%s", methodDec.getName()));

            // TODO 注目してるメソッドの引数だけでなく，内部で使用しているラムダ式の引数も一緒に取ってくるため，不整合が無いか検証
            methodDec.findAll(Parameter.class).forEach(param -> {
                String originalParamName = param.getNameAsString();
                String squashedName = squashPatternManager.getSquashedName(originalParamName);
                param.setName(squashedName);
            });

            methodDec.findAll(VariableDeclarationExpr.class).forEach(variable -> {
                // System.out.println(String.format("  %s", variable.toString()));
                List<VariableDeclarator> valDeclarators = getChildNodesByType(variable, VariableDeclarator.class);
                assert valDeclarators.size() == 1;
                VariableDeclarator declarator = valDeclarators.get(0);
                String originalVariableName = declarator.getNameAsString();
                String squashedName = squashPatternManager.getSquashedName(originalVariableName);

                declarator.setName(squashedName);
            });

            methodDec.findAll(NameExpr.class).forEach(nameExpr -> {
                String name = nameExpr.getNameAsString();
                if (squashPatternManager.hasPatternForName(name)) {
                    nameExpr.setName(squashPatternManager.getSquashedName(name));
                }
            });

            System.out.println();
            System.out.println(methodDec.toString());
            System.out.println(squashPatternManager.toString());
        });

        try {
            // TODO Ensure directory existence
            File file = new File(cmd.getOptionValue("outfile"));
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(cu.toString());
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static <N extends Node> List<N> getChildNodesByType(Node node, Class<N> clazz) {
        List<N> returnNodes = node.getChildNodes().stream()
            .filter(targetChild -> targetChild.getClass() == clazz)
            .map(n -> (N) n) // TODO unchecked警告を外す安全な方法が無いか調査
            .collect(Collectors.toList());

        return returnNodes;
    }

    private static void outputASTRecursively(int depth, Node node) {
        for (int i = 0; i < depth; i++) {
            System.out.print("  ");
        }

        if (node.toString().length() > 50) {
            System.out.println(String.format("%s, %s", node.getClass(), node.toString().replace("\n", "").replace("\r", "").substring(0, 50)));
        } else {
            System.out.println(String.format("%s, %s", node.getClass(), node.toString().replace("\n", "").replace("\r", "")));
        }

        node.getChildNodes().forEach(child -> {
            outputASTRecursively(depth + 1, child);
        });


    }

}

class SquashPatternManager {

    private BiMap<String, String> patternMap;

    SquashPatternManager() {
        patternMap = HashBiMap.create();
    }

    String getSquashedName(String originalName) {

        String squashedName;

        if (!patternMap.containsKey(originalName)) {
            int charIndex = 0;
            do {
                charIndex++;
                if (charIndex < originalName.length()) {
                    squashedName = originalName.substring(0, charIndex);
                } else {
                    squashedName = originalName + "_".repeat(charIndex - originalName.length());
                }
            } while (patternMap.containsValue(squashedName));
            patternMap.put(originalName, squashedName);
        } else {
            squashedName = patternMap.get(originalName);
        }

        return squashedName;
    }

    boolean hasPatternForName(String name) {
        return patternMap.containsKey(name);
    }

    @Override
    public String toString() {
        return patternMap.toString();
    }

}
