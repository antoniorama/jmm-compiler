package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(MAIN_METHOD_DECL, this::visitMainMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPRESSION_STMT, this::visitExpressionStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitImport(JmmNode node, Void unused) {
        return "import " + TypeUtils.convertImportName(node.get("name")) + END_STMT;
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        // class extends
        code.append(" extends ");
        if (node.hasAttribute("extendedClass")) {
            code.append(node.get("extendedClass"));
        }
        else {
            code.append("Object");
        }

        code.append(L_BRACKET);

        // class fields
        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("VarDecl")) {
                var paramCode = visit(child.getChild(0));
                code.append(".field public ").append(paramCode).append(END_STMT);
            }
        }

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor()).append(R_BRACKET);

        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // params
        int paramCount = 0;
        StringBuilder paramsCode = new StringBuilder();

        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("Param")) {
                paramCount++;

                if (paramCount > 1) {
                    paramsCode.append(", ");
                }

                var paramCode = visit(child);
                paramsCode.append(paramCode);
            }
        }

        if (!paramsCode.isEmpty()) {
            code.append("(").append(paramsCode).append(")");
        } else {
            code.append("()");
        }

        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType).append(L_BRACKET);

        // rest of its children stmts
        for (int i = paramCount + 1; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        code.append(R_BRACKET).append(NL);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        var child = node.getJmmChild(0);
        var lhs = exprVisitor.visit(child);
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        boolean isFieldAssignment = true;

        JmmNode parent = node.getParent();
        while (!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            parent = parent.getParent();
        }

        // Check if its local variable
        for (var local : table.getLocalVariables(parent.get("name"))) {
            if (local.getName().equals(child.get("name"))) {
                isFieldAssignment = false;
                break;
            }
        }


        // Check if it's a param
        if (isFieldAssignment) {
            for (var param : table.getParameters(parent.get("name"))) {
                if (param.getName().equals(child.get("name"))) {
                    isFieldAssignment = false;
                    break;
                }
            }
        }

        if (isFieldAssignment) {
            code.append("putfield(this, ").append(lhs.getCode()).append(", ").append(rhs.getCode()).append(").V;\n");
            return code.toString();
        }

        // code to compute the children
        code.append(lhs.getComputation()).append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(lhs.getCode()).append(SPACE).append(ASSIGN).append(typeString).append(SPACE).append(rhs.getCode()).append(END_STMT);

        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation()).append("ret").append(OptUtils.toOllirType(retType)).append(SPACE).append(expr.getCode()).append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        return id + typeCode;
    }

    private String visitMainMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method public static main(args.array.String).V");
        code.append(L_BRACKET);

        // Assuming your main method might contain statements or expressions
        for (JmmNode child : node.getChildren()) {
           var childCode = visit(child);
           code.append(childCode);
        }

        code.append("ret.V ;" + NL + R_BRACKET + NL);
        return code.toString();
    }

    private String visitExpressionStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (JmmNode child : node.getChildren()) {

            var childCode = exprVisitor.visit(child);
            code.append(childCode.getComputation());
        }

        return code.toString();
    }

    private String buildConstructor() {
        return ".construct " + table.getClassName() + "().V {\n" + "invokespecial(this, \"<init>\").V;\n" + "}\n";
    }

    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
