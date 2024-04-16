package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.*;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        // This logic had to be changed since we are treating Types in a different way in the Grammar!

        // TYPE.checkOrThrow(typeNode);

        if (typeNode.getKind() == "integerType") INTEGER_TYPE.checkOrThrow(typeNode);
        else if (typeNode.getKind() == "booleanType") BOOLEAN_TYPE.checkOrThrow(typeNode);
        else if (typeNode.getKind() == "stringType") STRING_TYPE.checkOrThrow(typeNode);
        else if (typeNode.getKind() == "voidType") VOID_TYPE.checkOrThrow(typeNode);
        // else if (typeNode.getKind().equals("OtherType")) OTHER_TYPE.checkOrThrow(typeNode);
        // TODO -> add the other types

        String typeName = "";
        if (typeNode.getKind().equals("OtherType")) typeName = typeNode.get("name");
        else typeName = typeNode.get("value");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            default -> typeName;
        };
    }


}
