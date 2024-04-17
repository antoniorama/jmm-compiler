package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String VOID_TYPE_NAME = "void";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBooleanTypeName() { return BOOLEAN_TYPE_NAME; }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {

        // Check for Array and VarArgs
        JmmNode actualExpr = expr;
        boolean isArray = false;
        boolean isVarArgs = false;
        if (expr.getKind().equals("ArrayType")) {
            actualExpr = expr.getJmmChild(0); // update actualExpr to correct one
            isArray = true;
        }

        else if (expr.getKind().equals("VarArgsType")) {
            actualExpr = expr.getJmmChild(0); // update actualExpr to correct one
            isArray = true;
            isVarArgs = true;
        }

        var kind = Kind.fromString(actualExpr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(actualExpr);
            case OTHER_TYPE, NEW_CLASS_INSTANCE -> getNewVarType(actualExpr);
            case ARRAY_INIT -> getArrayType(actualExpr, table);
            case NEW_ARRAY -> getNewArrayType(actualExpr);
            case INTEGER_TYPE, INTEGER_LITERAL, ARRAY_ACCESS -> new Type(INT_TYPE_NAME, isArray);
            case BOOLEAN_TYPE, BOOLEAN_VALUE -> new Type(BOOLEAN_TYPE_NAME, isArray);
            case VOID_TYPE -> new Type(VOID_TYPE_NAME, isArray);
            case METHOD_CALL_ON_ASSIGN, METHOD_CALL -> getMethodCallType(actualExpr, table);
            case VAR_REF_EXPR -> getVarRefType(actualExpr, table);
            case THIS -> getThisType(actualExpr, table);
            case PROPERTY_ACCESS -> getPropertyAccessType(actualExpr, table);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        // set custom attribute isVarArgs
        if (isVarArgs) {
            type.putObject("isVarArgs", true);
        }

        // System.out.println(type);
        return type;
    }

    private static Type getPropertyAccessType(JmmNode propertyAccessNode, SymbolTable table) {
        Type callerType = getExprType(propertyAccessNode.getJmmChild(0), table);

        // check for only valid property (length) and if the caller is an array
        if (propertyAccessNode.get("name").equals("length") && callerType.isArray()) {
            return new Type(INT_TYPE_NAME, false);
        }

        // if not a valid property, return null type
        return null;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getNewVarType(JmmNode varRefExpr) {
        String typeName = varRefExpr.get("name");
        return new Type(typeName, false);
    }

    private static Type getVarRefType(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");

        // Get the current method
        JmmNode parent = varRefExpr.getParent();
        while (!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            parent = parent.getParent();
        }
        String methodName = parent.get("name");

        List<Symbol> symbolTableOfLocalVars = table.getLocalVariables(methodName);
        List<Symbol> symbolTableOfParameters = table.getParameters(methodName);

        for (Symbol symbol : symbolTableOfLocalVars) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        for (Symbol symbol : symbolTableOfParameters) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        // Check in fields
        List<Symbol> fields = table.getFields();
        for (Symbol field : fields) {
            if (field.getName().equals(varName))
                return field.getType();
        }

        return null;
    }

    private static Type getArrayType(JmmNode arrayInit, SymbolTable table) {
        // TODO - possibly need to extend implementation
        // this function currently assumes that the Type of the array is the first element
        // if array has multiple elements this is previously treated in semantic analysis

        JmmNode expressionList = arrayInit.getJmmChild(0);
        JmmNode firstElement = expressionList.getJmmChild(0);
        Type firstElementType = getExprType(firstElement, table);
        return new Type(firstElementType.getName(), true);
    }

    private static Type getNewArrayType(JmmNode newArray) {
        JmmNode typeNode = newArray.getJmmChild(0);
        return new Type(typeNode.get("value"), true);
    }

    private static Type getMethodCallType(JmmNode methodCall, SymbolTable table) {
        return table.getReturnType(methodCall.get("methodName"));
    }

    private static Type getThisType(JmmNode methodCall, SymbolTable table) {
        return new Type(table.getClassName(), false);
    }

    public static String convertImportName(String rawImportName) {
        String[] parts = rawImportName.replace("[", "").replace("]", "").split(",");

        return Arrays.stream(parts)
                .map(String::trim)
                .collect(Collectors.joining("."));
    }
}
