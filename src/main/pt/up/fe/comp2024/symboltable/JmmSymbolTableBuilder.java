package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.awt.desktop.SystemEventListener;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pt.up.fe.comp2024.JavammParser.EXTENDS;
import static pt.up.fe.comp2024.JavammParser.TRUE;
import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {
        var imports = buildImports(root);

        var classDecl = root.getJmmChild(root.getNumChildren() - 1);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        var extendedClass = buildExtendedClass(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, extendedClass, methods, returnTypes, params, locals, imports, fields);
    }

    private static List<String> buildImports(JmmNode node){
        return node.getChildren(IMPORT_DECL).stream()
                .map(importDecl -> importDecl.get("name"))
                .toList();
    }

    private static String buildExtendedClass(JmmNode node){
        if(node.hasAttribute("extendedClass")) return node.get("extendedClass");
        return null;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        // Iterate over all children of the class declaration
        for (JmmNode varDecl : classDecl.getChildren()) {
            // Check if the child is a variable declaration
            if (varDecl.getKind().equals("VarDecl")) {
                // Get the name and type of the variable
                String fieldName = varDecl.getJmmChild(0).get("name");
                Type fieldType = TypeUtils.getExprType(varDecl.getJmmChild(0).getJmmChild(0), null);

                Symbol fieldSymbol = new Symbol(fieldType, fieldName);

                // Add the constructed Symbol to the fields list
                fields.add(fieldSymbol);
            }
        }

        return fields;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {

        Map<String, Type> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren()) {
            if (method.getKind().equals("MethodDecl")) {
                // get the last child, assuming that return is always last child
                Type returnType = TypeUtils.getExprType(method.getChild(0), null);
                map.put(method.get("name"), returnType);
            }
            else if (method.getKind().equals("MainMethodDecl")) {
                map.put(method.get("name"), new Type("void", false));
            }
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren()) {
            if (method.getKind().equals("MethodDecl")) {
                String methodName = method.get("name");
                List<Symbol> paramsList = map.getOrDefault(methodName, new ArrayList<>());
                for (JmmNode paramDecl : method.getChildren(PARAM)) {
                    Type paramType = TypeUtils.getExprType(paramDecl.getJmmChild(0), null);
                    paramsList.add(new Symbol(paramType, paramDecl.get("name")));
                }
                map.put(methodName, paramsList);
            }
            else if (method.getKind().equals("MainMethodDecl")) {
                // Main Method can be done hard-coded, assuming no parameters for main
                map.put("main", new ArrayList<>());
            }
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        List<JmmNode> methodDecls = classDecl.getChildren();
        for (JmmNode method : methodDecls) {
            if (method.getKind().equals("MethodDecl") || method.getKind().equals("MainMethodDecl")) {
                map.put(method.get("name"), getLocalsList(method));
            }
        }

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        List<String> methodNames = Stream.concat(
                        classDecl.getChildren(METHOD_DECL).stream(),
                        classDecl.getChildren(MAIN_METHOD_DECL).stream())
                .map(method -> method.get("name"))
                .collect(Collectors.toList());

        return methodNames;
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        List<Symbol> localsList = new ArrayList<>();
        List<JmmNode> varDecls = methodDecl.getChildren(VAR_DECL);

        // iterate the var declarations
        for (JmmNode varDecl : varDecls) {
            JmmNode param = varDecl.getChild(0);
            Type paramType = TypeUtils.getExprType(param.getChild(0), null);
            Symbol varSymbol = new Symbol(paramType, param.get("name"));
            localsList.add(varSymbol);
        }

        return localsList;
    }

}
