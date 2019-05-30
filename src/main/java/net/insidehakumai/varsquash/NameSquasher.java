package net.insidehakumai.varsquash;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Javaソースコードに含まれる変数名の短縮化を行います。
 * また，変数の元の名前と短縮化を行った後の名前のマッピングを保持します．
 * TODO Translate in English
 */
public class NameSquasher {

    private SquashFormat squashFormat;
    private BiMap<String, String> patternMap;

    NameSquasher(SquashFormat format) {
        squashFormat = format;
        patternMap = HashBiMap.create();
    }

    /**
     * Javaファイルの中で使用されている変数の名前を短縮し，その結果得られるソースコードをファイルに出力する
     * @param inputFilePath 変数の名前を短縮するJavaファイルのファイルパス
     * @param outputFilePath 変数の名前を短縮して得られるJavaソースコードの出力先ファイルパス
     * @throws FileNotFoundException 入力するJavaファイルが存在しなかった場合
     * TODO Translate in English
     */
    public void squashNamesInFile(String inputFilePath, String outputFilePath) throws IOException {

        ASTParser parser = ASTParser.newParser(AST.JLS11);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
        parser.setCompilerOptions(options);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setSource(readAll(inputFilePath).toCharArray());
        parser.setEnvironment(new String[] {}, new String[] {}, null, true);

        parser.createASTs(
            new String[]{inputFilePath},
            null,
            new String[] {},
            new FileASTRequestor() {
                @Override
                public void acceptAST(String sourceFilePath, CompilationUnit cu) {

                    for (Object typeDec : cu.types()) {
                        ((TypeDeclaration) typeDec).accept(new MyVisitor(cu));
                    }

                    super.acceptAST(sourceFilePath, cu);
                }
            },
            new NullProgressMonitor()
        );

//        CompilationUnit cu = (CompilationUnit) parser.createAST(new NullProgressMonitor());

//        for (Object typeDec : cu.types()) {
//            ((TypeDeclaration) typeDec).accept(new MyVisitor(cu));
//
//
////            for (FieldDeclaration fieldDec : ((TypeDeclaration) typeDec).getFields()) {
////                RefactoringDescriptor renameJavaElementDescriptor = renameFieldRefactoringContribution.createDescriptor();
////                 RenameFieldProcessor renameProcessor = new RenameFieldProcessor(fieldDec.);
////                 System.out.println(fieldDec);
////            }
//        }




//        cu.findfindAll(MethodDeclaration.class).forEach(methodDec -> {
//            // outputASTRecursively(0, methodDec);
//            System.out.println(String.format("%s", methodDec.getName()));
//
//            // TODO 注目してるメソッドの引数だけでなく，内部で使用しているラムダ式の引数も一緒に取ってくるため，不整合が無いか検証
//            methodDec.findAll(Parameter.class).forEach(param -> {
//                String originalParamName = param.getNameAsString();
//                String squashedName = getSquashedName(originalParamName);
//                param.setName(squashedName);
//            });
//
//            methodDec.findAll(VariableDeclarationExpr.class).forEach(variable -> {
//                // System.out.println(String.format("  %s", variable.toString()));
//                List<VariableDeclarator> valDeclarators = getChildNodesByType(variable, VariableDeclarator.class);
//                assert valDeclarators.size() == 1;
//                VariableDeclarator declarator = valDeclarators.get(0);
//                String originalVariableName = declarator.getNameAsString();
//                String squashedName = getSquashedName(originalVariableName);
//
//                declarator.setName(squashedName);
//            });
//
//            methodDec.findAll(NameExpr.class).forEach(nameExpr -> {
//                String name = nameExpr.getNameAsString();
//                if (hasPatternForName(name)) {
//                    nameExpr.setName(getSquashedName(name));
//                }
//            });
//
//            // System.out.println();
//            // System.out.println(methodDec.toString());
//        });

//        try {
//            // TODO Ensure directory existence
//            File file = new File(outputFilePath);
//            FileWriter fileWriter = new FileWriter(file);
//            fileWriter.write(cu.toString());
//            fileWriter.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * ある変数名について，その名前の短縮化を行った後の名前を返します。
     * 短縮化は、既にある名前を競合しないように一意に定まるものを設定します。
     * @param originalName 短縮化を行う変数名
     * @return 短縮化を行った後の名前
     * TODO Translate in English
     */
    String getSquashedName(String originalName) {

        String squashedName = "";

        if (!patternMap.containsKey(originalName)) {

            if (squashFormat == SquashFormat.FIRST_LETTER) {

                int charIndex = 0;
                do {
                    charIndex++;
                    if (charIndex < originalName.length()) {
                        squashedName = originalName.substring(0, charIndex);
                    } else {
                        squashedName = originalName + "_".repeat(charIndex - originalName.length());
                    }
                } while (patternMap.containsValue(squashedName));

            } else if (squashFormat == SquashFormat.DOLLAR) {

                int nameIndex = 0;
                while (patternMap.containsValue("$" + nameIndex)) {
                    nameIndex++;
                }
                squashedName = "$" + nameIndex;

            }

            patternMap.put(originalName, squashedName);
        } else {
            squashedName = patternMap.get(originalName);
        }

        return squashedName;
    }

    /**
     * ある変数名について、その名前に対応する短縮化を行った後の名前をこのクラスのオブジェクトが既に保持しているかどうかを返します
     * @param name 検索する名前
     * @return 短縮化を行った後の名前を保持していればTrue、保持していなかったらFalse
     * TODO Translate in English
     */
    boolean hasPatternForName(String name) {
        return patternMap.containsKey(name);
    }

//    /**
//     * ソースコードのAST上のあるノードの子ノードの中で，特定の種類のものを抽出します．
//     * @param node 子ノードを検索するノード。つまり，このメソッドが返すノード集合の親ノードとなるもの
//     * @param clazz 抽出するノードの種類を示すクラス
//     * @param <N> com.github.javaparser.ast.Node を継承しているクラス
//     * @return 抽出したノードのリスト。何も見つからなかった場合は空のリストを返す
//     * TODO Translate in English
//     */
//    private static <N extends Node> List<N> getChildNodesByType(Node node, Class<N> clazz) {
//        List<N> returnNodes = node.getChildNodes().stream()
//            .filter(targetChild -> targetChild.getClass() == clazz)
//            .map(n -> (N) n) // TODO unchecked警告を外す安全な方法が無いか調査
//            .collect(Collectors.toList());
//
//        return returnNodes;
//    }
//
//    private static void outputASTRecursively(int depth, Node node) {
//        for (int i = 0; i < depth; i++) {
//            System.out.print("  ");
//        }
//
//        if (node.toString().length() > 50) {
//            System.out.println(String.format("%s, %s", node.getClass(), node.toString().replace("\n", "").replace("\r", "").substring(0, 50)));
//        } else {
//            System.out.println(String.format("%s, %s", node.getClass(), node.toString().replace("\n", "").replace("\r", "")));
//        }
//
//        node.getChildNodes().forEach(child -> {
//            outputASTRecursively(depth + 1, child);
//        });
//
//
//    }

    public static String readAll(final String path) throws IOException {
        return Files.lines(Paths.get(path), Charset.forName("UTF-8"))
            .collect(Collectors.joining(System.getProperty("line.separator")));
    }

}

class MyVisitor extends ASTVisitor {

    private CompilationUnit accepteeCompilationUnit;

    MyVisitor(CompilationUnit accepteeCompilationUnit) {
        super();
        this.accepteeCompilationUnit = accepteeCompilationUnit;
    }

    @Override
    public boolean visit(FieldDeclaration fieldDec) {
        fieldDec.accept(new VariableDeclarationFragmentVisitor());
        return super.visit(fieldDec);
    }

    @Override
    public boolean visit(VariableDeclarationStatement valDecStmt) {
        valDecStmt.accept(new VariableDeclarationFragmentVisitor());
        return super.visit(valDecStmt);
    }

    @Override
    public boolean visit(SingleVariableDeclaration valDec) {

        // TODO VariableDeclarationFragmentVisitorと内容が重複しているからまとめられるか検討
        SimpleName variableName = valDec.getName();

        String bindingKey = variableName.resolveBinding() != null ? variableName.resolveBinding().getKey() : "[UNRESOLVED]";

        System.out.println(String.format("%3d %-50s %s",
            accepteeCompilationUnit.getLineNumber(valDec.getStartPosition()),
            variableName,
            bindingKey)
        );

        valDec.accept(new VariableDeclarationFragmentVisitor());
        return super.visit(valDec);
    }


    private class VariableDeclarationFragmentVisitor extends ASTVisitor {

        @Override
        public boolean visit(VariableDeclarationFragment valDecFragment) {

            SimpleName variableName = valDecFragment.getName();

            String bindingKey = variableName.resolveBinding() != null ? variableName.resolveBinding().getKey() : "[UNRESOLVED]";

            System.out.println(String.format("%3d %-50s %s",
                accepteeCompilationUnit.getLineNumber(valDecFragment.getStartPosition()),
                variableName,
                bindingKey)
            );

            return super.visit(valDecFragment);
        }

    }

}
