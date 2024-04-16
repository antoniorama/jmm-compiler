package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(NEW_CLASS_INSTANCE, this::visitNewClassInstance);
        addVisit(THIS, this::visitThis);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);

        if (type == null) {
            return new OllirExprResult(id);
        }
        String ollirType = OptUtils.toOllirType(type);
        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        List<String> argsCode = new ArrayList<>();

        // Visit the owner of the method (e.g., an instance of a class, or the class itself for static methods)
        OllirExprResult ownerExpr = visit(node.getJmmChild(0));
        computation.append(ownerExpr.getComputation());

        // Get the method name and return type
        String methodName = node.get("methodName");
        Type returnType = table.getReturnType(methodName);

        // Handle each argument of the method
        for (int i = 1; i < node.getNumChildren(); i++) {
            OllirExprResult argExpr = visit(node.getJmmChild(i));
            computation.append(argExpr.getComputation());
            argsCode.add(argExpr.getCode());
        }

        boolean isAssign = false;
        boolean isStatic = false;

        JmmNode parent = node.getParent();

        if (parent.getKind().equals("AssignStmt")) {
            isAssign = true;
        }

        // imported method
        if (returnType == null) {

            if (isAssign) {
                returnType = TypeUtils.getExprType(parent.getJmmChild(0), table);
            }
            else {
                JmmNode varRef = parent.getJmmChild(0);
                returnType = TypeUtils.getExprType(varRef, table);

                // Static method returns void
                if (returnType == null) {
                    isStatic = true;
                    returnType = new Type("void", false);
                }
            }
        }

        String returnTypeString = OptUtils.toOllirType(returnType);

        // Construct the method call
        String argsList = String.join(", ", argsCode);
        String tempVar = OptUtils.getTemp() + returnTypeString;
        String methodCallCode =  (isStatic ? "invokestatic(" : "invokevirtual(") + ownerExpr.getCode() + ", \"" + methodName + "\""
                + (argsCode.isEmpty() ? "" : ", " + argsList)
                + ")" + returnTypeString;

        // Store the result of the method call in a temporary variable
        if (isAssign) {
            computation.append(tempVar).append(SPACE).append(ASSIGN).append(returnTypeString).append(SPACE);
        }
        computation.append(methodCallCode).append(END_STMT);

        return new OllirExprResult(tempVar, computation.toString());
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult("this." + table.getClassName(), "");
    }

    private OllirExprResult visitNewClassInstance(JmmNode node, Void unused) {
        String className = node.get("name");
        String tempVar = OptUtils.getTemp() + "." + className;
        String initializationCode = tempVar + " " + ASSIGN + "." + className + " " + "new(" + className + ")." + className
                + END_STMT + "invokespecial(" + tempVar + ",\"<init>\").V" + END_STMT;
        return new OllirExprResult(tempVar, initializationCode);
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}
