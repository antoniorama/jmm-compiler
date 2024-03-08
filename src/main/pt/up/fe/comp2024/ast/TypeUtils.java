package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String VOID_TYPE_NAME = "void";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {

        // Check for Array and VarArgs
        JmmNode actualExpr = expr;
        boolean isArray = false;
        boolean isVarArgs = false;
        if (expr.getKind().equals("ArrayType")) {
            actualExpr = expr.getJmmChild(0); // update acutalExpr to correct one
            isArray = true;
        }

        else if (expr.getKind().equals("VarArgsType")) {
            actualExpr = expr.getJmmChild(0); // update acutalExpr to correct one
            isArray = true;
            isVarArgs = true;
        }

        var kind = Kind.fromString(actualExpr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(actualExpr);
            case OTHER_TYPE -> getVarExprType(actualExpr, table);
            case INTEGER_TYPE -> new Type(INT_TYPE_NAME, isArray);
            case BOOLEAN_TYPE -> new Type(BOOLEAN_TYPE_NAME, isArray);
            case VOID_TYPE -> new Type(VOID_TYPE_NAME, isArray);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        // set custom attribute isVarArgs
        if (isVarArgs) {
            type.putObject("isVarArgs", true);
        }

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*" -> new Type(INT_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String typeName = varRefExpr.get("name");
        return new Type(typeName, false);
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
