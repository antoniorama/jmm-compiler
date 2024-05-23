package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
        boolean isArray = false;
        boolean isVarArgs = false;
        if (expr.getKind().equals("ArrayType")) {
            expr = expr.getJmmChild(0); // update actualExpr to correct one
            isArray = true;
        }

        else if (expr.getKind().equals("VarArgsType")) {
            expr = expr.getJmmChild(0); // update actualExpr to correct one
            isArray = true;
            isVarArgs = true;
        }

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case OTHER_TYPE, NEW_CLASS_INSTANCE -> getNewVarType(expr);
            case ARRAY_INIT -> getArrayType(expr, table);
            case NEW_ARRAY -> getNewArrayType(expr);
            case INTEGER_TYPE, INTEGER_LITERAL, ARRAY_ACCESS -> new Type(INT_TYPE_NAME, isArray);
            case BOOLEAN_TYPE, BOOLEAN_VALUE, LOGICAL_EXPRESSION, RELATIONAL_EXPRESSION, NOT_EXPRESSION -> new Type(BOOLEAN_TYPE_NAME, isArray);
            case VOID_TYPE -> new Type(VOID_TYPE_NAME, isArray);
            case METHOD_CALL_ON_ASSIGN, METHOD_CALL -> getMethodCallType(expr, table);
            case VAR_REF_EXPR -> getVarRefType(expr, table);
            case THIS -> getThisType(table);
            case PROPERTY_ACCESS -> getPropertyAccessType(expr, table);
            case PARENTHESES_EXPRESSION -> getExprType(expr.getJmmChild(0), table);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        // set custom attribute isVarArgs
        if (isVarArgs) {
            assert type != null;
            type.putObject("isVarArgs", true);
        }

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

    private static boolean isMethodCallImported(JmmNode methodCall, SymbolTable table) {
        JmmNode child = methodCall.getJmmChild(0);
        Type childType = getExprType(child, table);
        return !(child.getKind().equals("This") || (childType != null && Objects.equals(childType.getName(), table.getClassName())));
    }

    private static Type getMethodCallType(JmmNode methodCall, SymbolTable table) {

        boolean isAssign = false;
        boolean isStatic = false;
        boolean isInsideReturn = false;
        JmmNode parent = methodCall.getParent();
        JmmNode child = methodCall.getJmmChild(0);
        Type returnType = table.getReturnType(methodCall.get("methodName"));
        Type childType = getExprType(child, table);

        if (returnType == null && parent.getKind().equals("PropertyAccess") && parent.get("name").equals("length")) {
            return new Type(INT_TYPE_NAME, true);
        }

        while(!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            if (parent.getKind().equals("AssignStmt")) {
                isAssign = true;
                break;
            }
            else if (parent.getKind().equals("ReturnStmt")) {
                isInsideReturn = true;
                break;
            }
            else if (parent.getKind().equals("MethodCall")) {
                break;
            }

            parent = parent.getParent();
        }

        boolean isImported = isMethodCallImported(methodCall, table);

        if (isImported && isInsideReturn) {
            return table.getReturnType(parent.get("name"));
        }

        if (childType == null) {
            isStatic = true;
        }

        // Return type of the variable being assigned
        if (isAssign && isImported) return TypeUtils.getExprType(parent.getJmmChild(0), table);

        // Return the normal type from the table
        if (isAssign) return returnType;

        // Return void
        if (isImported && isStatic && returnType == null) return new Type(VOID_TYPE_NAME, false);

        // Found a method call with the same name, return that type
        if (isImported && isStatic) return returnType;

        // Return the type of the child (imported class)
        if (isImported) return new Type(VOID_TYPE_NAME, false);

        if (!isStatic) return returnType;

        return new Type(VOID_TYPE_NAME, false);
    }

    private static Type getThisType(SymbolTable table) {
        return new Type(table.getClassName(), false);
    }

    public static String convertImportName(String rawImportName) {
        String[] parts = rawImportName.replace("[", "").replace("]", "").split(",");

        return Arrays.stream(parts)
                .map(String::trim)
                .collect(Collectors.joining("."));
    }
}
