package pt.up.fe.comp2024.analysis.passes;

import org.w3c.dom.Node;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

public class OtherSemantics extends AnalysisVisitor {

    private String currentMethod;


    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::verifyTypeCompatibility);
        addVisit(Kind.ARRAY_INIT, this::visitArrayInit);
        addVisit(Kind.ASSIGN_STMT, this::verifyTypeCompatibility);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
        addVisit(Kind.RETURN_STMT, this::verifyReturnType);
        addVisit(Kind.IF_STMT, this::verifyIfCondition);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.PARAM, this::visitParam);
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
        return true;
    }

    private boolean methodOnlyOneReturn(JmmNode method) {
        System.out.println(method.getChildren(RETURN_STMT));
        if (method.get("name").equals("main")) return method.getChildren(RETURN_STMT).size() == 1 || method.getChildren(RETURN_STMT).isEmpty();
        return method.getChildren(RETURN_STMT).size() == 1;
    }

    // Verifies type compatibility for BinaryOps and Assigns
    private Void verifyTypeCompatibility(JmmNode node, SymbolTable table) {
        String operator = "ASSIGN"; // we will consider "ASSIGN" to be an operator.
        // this way we don't have to do two separate functions for handling BinaryExpr and AssignStmt
        // and we can use the same logic for TypeCompatibility

        if (node.getKind().equals("BinaryExpr"))
            operator = node.get("op");

        Type leftType;
        if (node.getChild(0).getKind().equals("VarRefExpr")) {
            leftType = getVarType(node.getChild(0).get("name"), table, node);
        } else {
            leftType = TypeUtils.getExprType(node.getChild(0), table);
        }

        Type rightType;
        if (node.getChild(1).getKind().equals("VarRefExpr")) {
            rightType = getVarType(node.getChild(1).get("name"), table, node);
        } else {
            rightType = TypeUtils.getExprType(node.getChild(1), table);
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
        if(returnType != null && !returnType.equals(currentMethodReturnType)){
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
        System.out.println("VISITING ARRAY ACCESS");
        JmmNode arrayVar = arrayAccess.getChild(0);
        JmmNode arrayIndex = arrayAccess.getChild(1);

        // Error if index is not an integer
        if (!arrayIndex.getKind().equals(TypeUtils.getIntTypeName()) && !arrayIndex.getKind().equals("IntegerLiteral")) {
            var message = String.format("Trying to access array with index that is not an Integer");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        Type arrayVarType;
        if (arrayVar.getKind().equals("VarRefExpr")) {
            arrayVarType = getVarType(arrayAccess.getChild(0).get("name"), table, arrayAccess);
        } else {
            arrayVarType = TypeUtils.getExprType(arrayAccess.getChild(0), table);
        }


        // Error if variable isn't an array
        if (!arrayVarType.isArray()) {
            var message = String.format("Variable " + arrayVarType.getName() + " is not an array.");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table) {
        JmmNode expressionList = arrayInit.getJmmChild(0);
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

        String calledName = determineCalledName(methodCall, table);
        Type calledType = getVarTypeNoError(calledName, table, methodCall);
        String methodName = methodCall.get("methodName");

        if (isImportedMethodName(calledName, table)) {
            return null;
        }

        if (calledName.equals(table.getClassName()) || (calledType != null && calledType.getName().equals(table.getClassName()))) {
            if (isMethodNotDefinedInClass(methodCall, table)) {
                reportUndefinedMethod(methodCall, table);
                return null;
            }
        }

        if (calledType != null && isImportedMethodVar(calledType, table)) {
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
        if (!isVarArgs && !areArgumentTypesValid(argumentNodes, expectedParameters, table, methodCall)) {
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
        JmmNode methodCaller = methodCall.getJmmChild(0);
        if (methodCaller.getKind().equals("This")) {
            return table.getClassName();
        } else {
            return methodCaller.get("name");
        }
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
            arguments.add(methodCall.getJmmChild(i));
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

    private boolean areArgumentTypesValid(List<JmmNode> arguments, List<Symbol> expectedParameters, SymbolTable table, JmmNode node) {
        for (int i = 0; i < arguments.size(); i++) {
            if (!Objects.equals(getVarType(arguments.get(i).get("name"), table, node), expectedParameters.get(i).getType())) {
                return false;
            }
        }
        return true;
    }

    private void reportIncorrectArgumentTypes(JmmNode methodCall) {
        String message = "Incorrect types in method call";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
    }

    private Type getVarType(String varName, SymbolTable table, JmmNode node) {

        List<Symbol> symbolTableOfLocalVars = table.getLocalVariables(this.currentMethod);
        List<Symbol> symbolTableOfParameters = table.getParameters(this.currentMethod);

        System.out.println(symbolTableOfLocalVars);

        // Check if var is in local variables
        for (Symbol symbol : symbolTableOfLocalVars) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        // Check if var is in parameters
        for (Symbol symbol : symbolTableOfParameters) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        var message = String.format("Variable " + varName + " not found.");
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        return null;
    }

    private Type getVarTypeNoError(String varName, SymbolTable table, JmmNode node) {

        List<Symbol> symbolTableOfLocalVars = table.getLocalVariables(this.currentMethod);
        List<Symbol> symbolTableOfParameters = table.getParameters(this.currentMethod);

        // Check if var is in local variables
        for (Symbol symbol : symbolTableOfLocalVars) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        // Check if var is in parameters
        for (Symbol symbol : symbolTableOfParameters) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        return null;
    }

    private boolean areTypesCompatible(String operator, Type leftType, Type rightType, SymbolTable table) {
        String intTypeName = TypeUtils.getIntTypeName();

        return switch (operator) {
            case "+", "*" -> leftType.getName().equals(intTypeName) && rightType.getName().equals(intTypeName);
            case "ASSIGN" -> isAssignValid(leftType, rightType, table);
            default -> false;
        };
    }

    private boolean isAssignValid(Type leftType, Type rightType, SymbolTable table) {
        // TODO - probably isn't handling all possible cases

        // Check for array compatibility first. If one is an array and the other isn't, they can't be assigned regardless of their base type.
        if (leftType.isArray() != rightType.isArray()) {
            return false; // Directly return false if one is an array and the other isn't.
        }

        // If both types have the same name and array status, they are compatible.
        // This check is sufficient for both primitive types and class types, including arrays, since we already checked array status compatibility.
        if (leftType.getName().equals(rightType.getName())) {
            return true;
        }

        // ObjectAssignmentPassExtends
        if (rightType.getName().equals(table.getClassName()) && leftType.getName().equals(table.getSuper())) {
            return true;
        }

        // ObjectAssignmentPassImports
        List<String> importedList = table.getImports();
        if (importedList.contains(rightType.getName()) && importedList.contains(leftType.getName())) {
            return true;
        }

        return false;
    }

    private Void verifyIfCondition(JmmNode ifNode, SymbolTable table) {
        JmmNode conditionNode = ifNode.getChild(0);

        // Check if condition is a boolean variable
        // TODO -> test this, there are no semantic tests for this
        if (conditionNode.getKind().equals("VarRefExpr")) {
            Type conditionType = getVarType(conditionNode.get("name"), table, conditionNode);
            assert conditionType != null;
            if (conditionType.getName().equals("boolean")) {
                return null;
            }
        }

        // TODO -> Check if condition is a boolean expression

        // TODO -> Check if condition is Method Call that returns boolean

        // If condition isn't none of the above, add error report
        var message = "If condition not valid";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(ifNode), NodeUtils.getColumn(ifNode), message, null));
        return null;
    }

    private Void visitVarDecl(JmmNode varDeclNode, SymbolTable table) {
        String varName = varDeclNode.getChild(0).get("name");

        // Check fields for varargs
        if (checkForVarArgs(varName, table.getFields(), varDeclNode)) {
            return null;
        }

        // Check local variables for varargs
        if (checkForVarArgs(varName, table.getLocalVariables(currentMethod), varDeclNode)) {
            return null;
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

    private Void visitParam(JmmNode paramNode, SymbolTable table) {
        // Check if param name is not length
        if (Objects.equals(paramNode.get("name"), "length")) {
            String message = "Can't name this 'length'";
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(paramNode), NodeUtils.getColumn(paramNode), message, null));
        }

        return null;
    }
}
