package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

public class OptUtils {
    private static int tempNumber = -1;
    private static int ifThenNumber = -1;
    private static int whileNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {
        return ++tempNumber;
    }

    public static int getNextIfThenNum() {

        return ++ifThenNumber;
    }

    public static int getNextWhileNum() {
        return ++whileNumber;
    }

    public static String toOllirType(JmmNode node) {

        // This logic had to be changed since we are treating Types in a different way in the Grammar!
        String kind = node.getKind();

        switch (kind) {
            case "integerType" -> INTEGER_TYPE.checkOrThrow(node);
            case "booleanType" -> BOOLEAN_TYPE.checkOrThrow(node);
            case "stringType" -> STRING_TYPE.checkOrThrow(node);
            case "voidType" -> VOID_TYPE.checkOrThrow(node);
            case "arrayType" -> ARRAY_TYPE.checkOrThrow(node);
        }
        // TODO -> add the other types

        String typeName = switch (kind) {
            case "OtherType" -> node.get("name");
            case "ArrayType", "VarArgsType" -> "array";
            default -> node.get("value");
        };

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        if (type.isArray()) return toOllirType("array");
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "array" -> "array.i32";
            default -> typeName;
        };
    }


}
