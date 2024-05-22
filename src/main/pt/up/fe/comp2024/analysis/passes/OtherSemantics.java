package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

public class OtherSemantics extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMainMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::verifyTypeCompatibility);
        addVisit(Kind.ASSIGN_STMT, this::verifyTypeCompatibility);
        addVisit(RELATIONAL_EXPRESSION, this::verifyTypeCompatibility);
        addVisit(LOGICAL_EXPRESSION, this::verifyTypeCompatibility);
        addVisit(Kind.ARRAY_INIT, this::visitArrayInit);
        addVisit(Kind.NEW_ARRAY, this::visitNewArray);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
        addVisit(Kind.RETURN_STMT, this::verifyReturnType);
        addVisit(Kind.IF_STMT, this::verifyIfCondition);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(CLASS_DECL, this::visitClassDecl);
        addVisit(PROPERTY_ACCESS, this::verifyPropertyAccess);
        addVisit(EXPRESSION_STMT, this::verifyExpressionStmt);
    }

    private Void verifyPropertyAccess(JmmNode propertyAccess, SymbolTable table) {
        String propertyName = propertyAccess.get("name");

        if (!propertyName.equals("length")) {
            String message = String.format("Property '%s' does not exist in class '%s'", propertyName, table.getClassName());
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(propertyAccess), NodeUtils.getColumn(propertyAccess), message, null));
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        // if there is a varArgs parameter that is not the last one -> error!
        List<Symbol> methodParameters = table.getParameters(method.get("name"));
        for (int i = 0; i < methodParameters.size() - 1; i++) {
            if (methodParameters.get(i).getType().hasAttribute("isVarArgs")) {
                var message = "Varargs parameter is not the last one!";
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(method), NodeUtils.getColumn(method), message, null));
            }
        }

        // Check for duplicate local variables within method
        List<Symbol> locals = table.getLocalVariables(method.get("name"));
        Set<Symbol> uniqueLocals = new HashSet<>(locals);

        if (uniqueLocals.size() != locals.size()) {
            String message = "Method can't have duplicate local variables";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(method), NodeUtils.getColumn(method), message, null));
        }

        // Check for duplicate parameters in method call
        List<Symbol> parameters = table.getParameters(method.get("name"));
        Set<Symbol> uniqueParameters = new HashSet<>(parameters);

        if (uniqueParameters.size() != parameters.size()) {
            String message = "Method can't have duplicate parameters";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(method), NodeUtils.getColumn(method), message, null));
        }

        // Return Checks
        if (!methodReturnChecks(method)) {
            var message = "Illegal number of return statements in method " + method.get("name");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(method), NodeUtils.getColumn(method), message, null));
        }

        currentMethod = method.get("name");
        return null;
    }

    private boolean methodReturnChecks(JmmNode method) {
        // Check if only one return exists in method
        if (!methodOnlyOneReturn(method)) return false;
        return methodReturnIsLast(method);
    }

    private boolean methodOnlyOneReturn(JmmNode method) {
        if (method.get("name").equals("main")) return method.getChildren(RETURN_STMT).size() == 1 || method.getChildren(RETURN_STMT).isEmpty();
        return method.getChildren(RETURN_STMT).size() == 1;
    }

    private boolean methodReturnIsLast(JmmNode method) {
        if (method.get("name").equals("main") && method.getChildren(RETURN_STMT).isEmpty()) return true;
        String lastKind = method.getChild(method.getNumChildren() - 1).getKind();
        return lastKind.equals("ReturnStmt");
    }

    // Verifies type compatibility for BinaryOps and Assigns
    private Void verifyTypeCompatibility(JmmNode node, SymbolTable table) {
        String operator = "ASSIGN"; // we will consider "ASSIGN" to be an operator.
        // this way we don't have to do two separate functions for handling BinaryExpr and AssignStmt
        // and we can use the same logic for TypeCompatibility

        if (node.getKind().equals("BinaryExpr") || node.getKind().equals("LogicalExpression") || node.getKind().equals("RelationalExpression"))
            operator = node.get("op");

        JmmNode leftNode = node.getChild(0);

        if (Objects.equals(operator, "ASSIGN") && (leftNode.getKind().equals("IntegerLiteral") || leftNode.getKind().equals("BooleanValue") || leftNode.getKind().equals("ExpressionSmt"))) {
            var message = "Can't assign to int literals";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
            return null;
        }

        Type leftType = TypeUtils.getExprType(leftNode, table);
        Type rightType = TypeUtils.getExprType(node.getChild(1), table);

        if (Objects.equals(operator, "op") && (!leftType.getName().equals("int") || !rightType.getName().equals("int"))) {
            var message = "Binary operator '" + operator + "' can only be used with integers";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        // Check if any of the types comes from an import
        // In this case we assume that the types are assignable

        if (Objects.equals(operator, "ASSIGN") && (leftNode.getKind().equals("IntegerLiteral") || leftNode.getKind().equals("BooleanValue"))) {
            var message = "Can't assign to int or boolean literals";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        if (rightType == null) {
            return null;
        }

        // Arrays cannot be used in arithmetic operations
        if (!Objects.equals(operator, "ASSIGN") && (leftType.isArray() || rightType.isArray())) {
            var message = "Arrays cannot be used in arithmetic operations";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
            return null;
        }

        // Verify the compatibility of the types and handle the error report
        if (!areTypesCompatible(operator, leftType, rightType, table)) {
            // not sure if Kinds print for Strings?
            var message = String.format("Incompatible types for operator '" + operator + "': '" + leftType + "' and '" + rightType + "'");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        return null;
    }

    private Void verifyReturnType(JmmNode returnNode, SymbolTable table){
        Type currentMethodReturnType = table.getReturnType(currentMethod);
        Type returnType = TypeUtils.getExprType(returnNode.getChild(0), table);

        // Check for invalid return type
        if (returnType == null) {
            var message = "Invalid return type";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnNode), NodeUtils.getColumn(returnNode), message, null));
        }

        if (returnType != null && !returnType.equals(currentMethodReturnType)){
            var message = String.format("The return type '%s' does not match the method's return type '%s'.", returnType, currentMethodReturnType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnNode), NodeUtils.getColumn(returnNode), message, null));
        }

        // Verify if return isn't VarArgs
        if (currentMethodReturnType.hasAttribute("isVarArgs")) {
            var message = "Can't return VarArgs";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnNode), NodeUtils.getColumn(returnNode), message, null));
        }

        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        JmmNode arrayVar = arrayAccess.getChild(0);
        JmmNode arrayIndex = arrayAccess.getChild(1);

        Type type = TypeUtils.getExprType(arrayIndex, table);

        // Error if index is not an integer
        if (!type.getName().equals("int")) {
            var message = "Trying to access array with index that is not an Integer";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        // Error if arrayVar is a method call
        if (arrayVar.getKind().equals("MethodCall")) {
            var message = "Method call cannot be used as array";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        Type arrayVarType = TypeUtils.getExprType(arrayVar, table);

        // Error if variable isn't an array
        if (!arrayVarType.isArray()) {
            var message = String.format("Variable " + arrayVarType.getName() + " is not an array.");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }
        
        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table) {
        JmmNode expressionList = arrayInit.getChild(0);
        List<JmmNode> elements = expressionList.getChildren();

        // if array has length 0, then it's valid
        if (elements.isEmpty()) {
            return null;
        }

        Type firstElementType = TypeUtils.getExprType(elements.get(0), table);

        for (int i = 1; i < elements.size(); i++) {
            Type currentElementType = TypeUtils.getExprType(elements.get(i), table);

            if (!firstElementType.equals(currentElementType)) {
                var message = String.format("Array initialization contains elements of different types: %s and %s.", firstElementType.getName(), currentElementType.getName());
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayInit), NodeUtils.getColumn(arrayInit), message, null));
                break; // Reporting one error for type inconsistency is enough
            }
        }

        return null;
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {

        if (isMethodFromSuperClass(table)) {
            return null;
        }

        String callerName = determineCalledName(methodCall, table);
        Type callerType = TypeUtils.getExprType(methodCall.getChild(0), table);

        Type calledType = TypeUtils.getExprType(methodCall, table);
        String methodName = methodCall.get("methodName");

        // There are two possible cases for imports

        // 1. Using import name directly
        if (isImportedMethodName(callerName, table)) {
            return null;
        }

        // 2. Using var and imported is type of var
        if (callerType != null && isImportedMethodName(callerType.getName(), table)) {
            return null;
        }

        if (callerName.equals(table.getClassName()) || (calledType != null && calledType.getName().equals(table.getClassName()))) {
            if (isMethodNotDefinedInClass(methodCall, table)) {
                reportUndefinedMethod(methodCall, table);
                return null;
            }
        }

        if (calledType != null && isImportedMethodVar(calledType, table)) {
            return null;
        }

        // If it hasn't return in the previous code, the only option is for the method to be from current class
        // Check this first
        if (!table.getMethods().contains(methodName)) {
            String message = String.format("Method %s doesn't exist in class %s", methodCall.get("methodName"), table.getClassName());
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
            return null;
        }

        List<JmmNode> argumentNodes = collectArgumentNodes(methodCall);
        List<Symbol> expectedParameters = table.getParameters(methodName);
        boolean isVarArgs = false;
        if (!argumentNodes.isEmpty()) isVarArgs = isMethodVarArgs(methodName, table);

        if (!isValidArgumentCount(argumentNodes, expectedParameters, isVarArgs)) {
            reportIncorrectNumberOfArguments(methodCall);
            return null;
        }

        // TODO -> verify that each varargs param has correct type
        // This would be implemented inside areArgumentTypesValid
        if (!isVarArgs && !areArgumentTypesValid(argumentNodes, expectedParameters, table)) {
            reportIncorrectArgumentTypes(methodCall);
            return null;
        }

        return null;
    }

    private boolean isMethodVarArgs(String methodName, SymbolTable table) {
        Type lastParamType = table.getParameters(methodName).get(table.getParameters(methodName).size() - 1).getType();
        return lastParamType.hasAttribute("isVarArgs");
    }

    private boolean isMethodFromSuperClass(SymbolTable table) {
        return table.getSuper() != null;
    }

    private String determineCalledName(JmmNode methodCall, SymbolTable table) {
        JmmNode methodCaller = methodCall.getChild(0);
        if (methodCaller.getKind().equals("This")) return table.getClassName();
        if (methodCaller.getKind().equals("ParenthesesExpression")) return determineCalledName(methodCaller, table);

        return methodCaller.get("name");
    }

    private boolean isImportedMethodName(String methodName, SymbolTable table) {
        return table.getImports().contains(methodName);
    }

    private boolean isImportedMethodVar(Type methodType, SymbolTable table) {
        return table.getImports().contains(methodType.getName());
    }

    private boolean isMethodNotDefinedInClass(JmmNode methodCall, SymbolTable table) {
        return !table.getMethods().contains(methodCall.get("methodName"));
    }

    private void reportUndefinedMethod(JmmNode methodCall, SymbolTable table) {
        String message = String.format("Method %s doesn't exist in class %s", methodCall.get("methodName"), table.getClassName());
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
    }

    private List<JmmNode> collectArgumentNodes(JmmNode methodCall) {
        List<JmmNode> arguments = new ArrayList<>();
        for (int i = 1; i < methodCall.getNumChildren(); i++) {
            arguments.add(methodCall.getChild(i));
        }
        return arguments;
    }

    private boolean isValidArgumentCount(List<JmmNode> argumentNodes, List<Symbol> expectedParameters, boolean isVarArgs) {
        if (isVarArgs) {
            int minArgs = expectedParameters.size() - 1; // Varargs methods can take at least the non-varargs arguments
            return argumentNodes.size() >= minArgs;
        } else {
            return argumentNodes.size() == expectedParameters.size();
        }
    }

    private void reportIncorrectNumberOfArguments(JmmNode methodCall) {
        String message = "Incorrect number of arguments for method call";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
    }

    private boolean areArgumentTypesValid(List<JmmNode> arguments, List<Symbol> expectedParameters, SymbolTable table) {
        for (int i = 0; i < arguments.size(); i++) {

            Type expectedType = expectedParameters.get(i).getType();
            Type actualType = TypeUtils.getExprType(arguments.get(i), table);

            if (!expectedType.equals(actualType)) {
                return false;
            }
        }
        return true;
    }

    private void reportIncorrectArgumentTypes(JmmNode methodCall) {
        String message = "Incorrect types in method call";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
    }

    private boolean areTypesCompatible(String operator, Type leftType, Type rightType, SymbolTable table) {
        String intTypeName = TypeUtils.getIntTypeName();
        String booleanTypeName = TypeUtils.getBooleanTypeName();

        return switch (operator) {
            case "+", "*", "/", "-", "<" -> leftType.getName().equals(intTypeName) && rightType.getName().equals(intTypeName);
            case "ASSIGN" -> isAssignValid(leftType, rightType, table);
            case "&&" -> leftType.getName().equals(booleanTypeName) && rightType.getName().equals(booleanTypeName);
            default -> false;
        };
    }

    private boolean isAssignValid(Type leftType, Type rightType, SymbolTable table) {

        // Check if rightType wasn't valid
        if (rightType == null || leftType == null) {
            return false;
        }

        // Check if types are the same
        if (leftType.equals(rightType)) {
            return true;
        }

        // ObjectAssignmentPassExtends
        if (rightType.getName().equals(table.getClassName()) && leftType.getName().equals(table.getSuper())) {
            return true;
        }

        // ObjectAssignmentPassImports
        List<String> importedList = table.getImports();
        return importedList.contains(rightType.getName()) && importedList.contains(leftType.getName());
    }

    private Void verifyIfCondition(JmmNode ifNode, SymbolTable table) {
        JmmNode conditionNode = ifNode.getChild(0);

        Type conditionType = TypeUtils.getExprType(conditionNode, table);

        // check if condition is boolean
        if (conditionType == null || !conditionType.getName().equals("boolean") || conditionType.isArray()) {
            String message = String.format("If condition must be a boolean, but found type '%s'.", conditionType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(conditionNode), NodeUtils.getColumn(conditionNode), message, null));
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDeclNode, SymbolTable table) {
        String varName = varDeclNode.getChild(0).get("name");

        // Check fields for varargs
        if (checkForVarArgs(varName, table.getFields(), varDeclNode)) {
            return null;
        }

        // Check local variables for varargs
        if (currentMethod != null) {
            checkForVarArgs(varName, table.getLocalVariables(currentMethod), varDeclNode);
        }

        return null;
    }

    private boolean checkForVarArgs(String varName, List<Symbol> symbols, JmmNode varDeclNode) {
        for (Symbol s : symbols) {
            Type type = s.getType();
            if (s.getName().equals(varName) && type.hasAttribute("isVarArgs")) {
                String message = "Varargs are not allowed here";
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(varDeclNode), NodeUtils.getColumn(varDeclNode), message, null));
                return true;
            }
        }
        return false;
    }

    private Void visitMainMethodDecl(JmmNode mainMethod, SymbolTable table) {
        // Check if there is any THIS in descendants
        if (!mainMethod.getDescendants(THIS).isEmpty()) {
            String message = "Can't use 'this' in main method";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(mainMethod), NodeUtils.getColumn(mainMethod), message, null));
        }

        // Prepare a set of all field names for quick lookup
        Set<String> fieldNames = table.getFields().stream()
                .map(Symbol::getName)
                .collect(Collectors.toSet());

        Set<String> parameterNames = table.getParameters(mainMethod.get("name")).stream()
                .map(Symbol::getName)
                .collect(Collectors.toSet());

        Set<String> localVariableNames = table.getLocalVariables(mainMethod.get("name")).stream()
                .map(Symbol::getName)
                .collect(Collectors.toSet());

        // Check if any var ref is a field
        List<JmmNode> varRefNodes = mainMethod.getDescendants(VAR_REF_EXPR);
        for (JmmNode varRef : varRefNodes) {
            String varName = varRef.get("name");
            // If the variable name is in the set of fields, and it's not a local variable nor a parameter, add a report
            if (fieldNames.contains(varName) && !parameterNames.contains(varName) && !localVariableNames.contains(varName)) {
                // Variable reference is a field, add a report
                String message = String.format("Variable reference '%s' in static main method cannot be a field", varName);
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(varRef), NodeUtils.getColumn(varRef), message, null));
            }
        }

        currentMethod = mainMethod.get("name");
        return null;
    }

    private Void visitWhileStmt(JmmNode whileNode, SymbolTable table) {
        JmmNode conditionNode = whileNode.getChild(0);
        Type conditionType = TypeUtils.getExprType(conditionNode, table);

        if (conditionType == null || !conditionType.getName().equals("boolean") || conditionType.isArray()) {
            String message = String.format("While condition must be a boolean, but found type '%s'.", conditionType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(conditionNode), NodeUtils.getColumn(conditionNode), message, null));
        }

        return null;
    }

    private Void visitNewArray(JmmNode newArrayNode, SymbolTable table) {
        JmmNode lengthNode = newArrayNode.getChild(1);

        // Check if array is initialized with integer length
        if (!lengthNode.getKind().equals("IntegerLiteral")) {
            String message = "Array must have integer length";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(newArrayNode), NodeUtils.getColumn(newArrayNode), message, null));
        }

        return null;
    }

    private Void visitClassDecl(JmmNode classDeclNode, SymbolTable table) {
        // Regex to extract the last part of the import string
        Pattern pattern = Pattern.compile(".*\\.([^.]+)$");

        // Check for duplicate imports based on the last part
        List<String> imported = table.getImports();
        Set<String> lastParts = new HashSet<>();
        boolean hasDuplicateImports = false;

        for (String importStr : imported) {
            Matcher matcher = pattern.matcher(importStr);
            if (matcher.find()) {
                String lastPart = matcher.group(1);
                if (!lastParts.add(lastPart)) {
                    hasDuplicateImports = true;
                    break;
                }
            }
        }

        if (hasDuplicateImports) {
            String message = "This class can't have duplicate imports!";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(classDeclNode), NodeUtils.getColumn(classDeclNode), message, null));
        }

        // Check if there are no duplicated methods
        List<String> methods = table.getMethods();
        Set<String> uniqueMethods = new HashSet<>(methods);

        if (uniqueMethods.size() != methods.size()) {
            String message = "This class can't have duplicate methods!";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(classDeclNode), NodeUtils.getColumn(classDeclNode), message, null));
        }

        // Check if there are no duplicate fields
        List<Symbol> fields = table.getFields();
        Set<Symbol> uniqueFields = new HashSet<>(fields);

        if (uniqueFields.size() != fields.size()) {
            String message = "This class can't have duplicate fields!";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(classDeclNode), NodeUtils.getColumn(classDeclNode), message, null));
        }

        return null;
    }

    private Void verifyExpressionStmt(JmmNode expressionStmt, SymbolTable table) {
        List<JmmNode> children = expressionStmt.getChildren();

        for (JmmNode child : children) {
            if (child.getKind().equals("MethodCall")) {
                return null;
            }
        }
        String message = "Expression statement must contain a method call";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(expressionStmt), NodeUtils.getColumn(expressionStmt), message, null));
        return null;
    }
}
