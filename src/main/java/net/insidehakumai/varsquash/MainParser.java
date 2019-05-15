package net.insidehakumai.varsquash;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.contexts.FieldAccessContext;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.HashBiMap;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.units.qual.Length;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;



/**
 * JavaMethodParserのエントリーポイント用クラス
 */
public class MainParser {

    public static void main(String[] args) {
        Options options = new Options();

        Option trainDirOpt = new Option("i", "infile", true, "path of file to be deranged" );
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

        cu.findAll(MethodDeclaration.class).stream().forEach(methodDec -> {
            Map<String, String> variableShorteningMap = HashBiMap.create();
            // outputASTRecursively(0, methodDec);
            System.out.println(String.format("%s", methodDec.getName()));

            // TODO 注目してるメソッドの引数だけでなく，内部で使用しているラムダ式の引数も一緒に取ってくるため，不整合が無いか検証
            methodDec.findAll(Parameter.class).stream().forEach(param -> {
                String originalParamName = param.getNameAsString();
                String shortenedName = String.valueOf(originalParamName.toString().charAt(0));;

                if (!variableShorteningMap.containsKey(originalParamName)) {
                    int charIndex = 0;
                    do {
                        charIndex++;
                        if (charIndex < originalParamName.length()) {
                            shortenedName = originalParamName.substring(0, charIndex);
                        } else {
                            shortenedName = originalParamName;
                            for (int i = 0; i <= originalParamName.length() - charIndex; i++) {
                                shortenedName += "_";
                            }
                        }
                    } while(variableShorteningMap.containsValue(shortenedName));
                    variableShorteningMap.put(originalParamName, shortenedName);
                } else {
                    shortenedName = variableShorteningMap.get(originalParamName);
                }
                param.setName(shortenedName);
            });

            methodDec.findAll(VariableDeclarationExpr.class).stream().forEach(variable -> {
                // System.out.println(String.format("  %s", variable.toString()));
                List<VariableDeclarator> valDeclarators = getChildNodesByType(variable, VariableDeclarator.class);
                assert valDeclarators.size() == 1;
                VariableDeclarator declarator = valDeclarators.get(0);
                String originalVariableName = declarator.getNameAsString();
                String shortenedName = String.valueOf(originalVariableName.toString().charAt(0));

                // TODO 上にほとんど同じ処理があるのでメソッド化してまとめる
                if (!variableShorteningMap.containsKey(originalVariableName)) {
                    int charIndex = 0;
                    do {
                        charIndex++;
                        if (charIndex < originalVariableName.length()) {
                            shortenedName = originalVariableName.substring(0, charIndex);
                        } else {
                            shortenedName = originalVariableName;
                            for (int i = 0; i <= originalVariableName.length() - charIndex; i++) {
                                shortenedName += "_";
                            }
                        }
                    } while(variableShorteningMap.containsValue(shortenedName));
                    variableShorteningMap.put(originalVariableName, shortenedName);
                } else {
                    shortenedName = variableShorteningMap.get(originalVariableName);
                }
                declarator.setName(shortenedName);
            });

            methodDec.findAll(NameExpr.class).stream().forEach(nameExpr -> {
                String name = nameExpr.getNameAsString();
                if (variableShorteningMap.containsKey(name)) {
                    nameExpr.setName(variableShorteningMap.get(name));
                }
            });

            System.out.println();            
            System.out.println(methodDec.toString());
            System.out.println(variableShorteningMap.toString());
        });

        try {
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
        .filter(targetChild -> {
            return targetChild.getClass() == clazz;
        })
        .map(n -> (N) n) // TODO unchecked警告を外す安全な方法が無いか調査
        .collect(Collectors.toList());

        return returnNodes;
    }

    private static void outputASTRecursively(int depth, Node node) {
        for (int i = 0; i < depth; i++) {
            System.out.print("  ");
        }

        if (node.toString().length() > 50) {
            System.out.println(String.format("%s, %s",node.getClass(), node.toString().replace("\n", "").replace("\r", "").substring(0, 50)));
        } else {
            System.out.println(String.format("%s, %s",node.getClass(), node.toString().replace("\n", "").replace("\r", "")));
        }

        node.getChildNodes().forEach(child -> {
           outputASTRecursively(depth + 1, child); 
        });


    }


    /**
     * ファイルの絶対パスからファイル名のみの文字列を返す
     * @param fileName ファイルパスを表す文字列
     * @return ファイル名のみの文字列
     */
    private static String getPrefix(String fileName) {
        if (fileName == null)
            return null;
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            fileName = fileName.substring(0, point);
        }
        point = fileName.lastIndexOf("/");
        if (point != -1) {
            return fileName.substring(point + 1);
        }
        return fileName;
    }

    /**
     * ディレクトリの内部に含まれるJavaファイルを再帰的に探索する
     * @param dir 探索対象のディレクトリを表すFileオブジェクト
     * @return 内部に含まれるJavaファイルを表すFileオブジェクトのArrayList
     */
    private static ArrayList<File> getJavaFilesRecursively(File dir) {
        ArrayList<File> fileList = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return new ArrayList<>();

        for (File file : files) {
            if (!file.exists()) {
                continue;
            } else if (file.isDirectory()) {
                fileList.addAll(getJavaFilesRecursively(file));
            } else if (file.isFile() && FilenameUtils.getExtension(file.getAbsolutePath()).equals("java")) {
                fileList.add(file);
            }
        }
        return fileList;
    }

    private static String formatDirName(String dirName){
        return dirName.replace("-", "").replace("_", "");
    }

}
