package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(ReturnInstruction.class, this::generateReturn);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // Check if there is a class extends
        var superName = Objects.equals(ollirResult.getOllirClass().getSuperClass(), "null") ? ollirResult.getOllirClass().getSuperClass() : "java/lang/Object";
        if (!Objects.equals(superName, "")) {
            code.append(".super ").append(superName).append(NL);
        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(superName);

        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;
        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        ArrayList<Element> paramList = method.getParams();
        var params = new StringBuilder();
        for (Element p : paramList) {
            params.append(ollirTypeToChar(p.getType()));
        }
        String returnType = ollirTypeToChar(method.getReturnType());

        // Add static on main method
        String staticString = "";
        if (Objects.equals(method.getMethodName(), "main")) {
            staticString = "static ";
        }

        code.append("\n.method ").append(modifier).append(staticString).append(methodName).append("(").append(params).append(")").append(returnType).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String ollirTypeToChar(Type ollirType) {
        // TODO - kind of an hardcoded implementation, might be a better way
        if (ollirType.toString().equals("INT32")) {
            return "I";
        } else if (ollirType.toString().equals("BOOLEAN")) {
            return "Z";
        } else if (ollirType.toString().equals("STRING[]")) {
            return "[Ljava/lang/String;";
        }
        return "V";
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        String storeInstruction = getStoreInstruction(operand.getType());
        code.append(storeInstruction).append(" ").append(reg).append(NL);

        return code.toString();
    }

    private String getStoreInstruction(Type type) {
        return switch (type.toString()) {
            case "INT32" -> "istore";
            case "BOOLEAN" -> "istore";
            default -> {
                // Dynamically handle any type starting with "OBJECTREF"
                if (type.toString().startsWith("OBJECTREF")) {
                    yield "astore";
                } else {
                    throw new NotImplementedException("Store instruction not implemented for type: " + type);
                }
            }
        };
    }


    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be

        // If the return is void, then .getOperand() is null, not sure how to handle this
        if (returnInst.getOperand() == null) {
            code.append("return").append(NL);
        } else {
            code.append(generators.apply(returnInst.getOperand()));
            code.append("ireturn").append(NL);
        }

        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();

        // Check if it's a constructor call
        var className = ollirResult.getOllirClass().getClassName();
        if (call.getInvocationType() == CallType.NEW) {
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL);

            code.append("invokespecial ").append(className).append("/<init>()V").append(NL);
        }

        // TODO: Expand this section to handle arguments and other call types

        return code.toString();
    }


}
