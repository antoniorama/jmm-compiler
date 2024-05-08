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
        ollirResult.getOllirClass().getImports().add("java/lang/Object");

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
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(Field.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(CallInstruction.class, this::generateCall);
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
        code.append(".class public ").append(className).append(NL).append(NL);

        String superClass = ollirResult.getOllirClass().getSuperClass();
        if (superClass == null)
            superClass = "Object";
        String superName = getFullName(superClass);
        code.append(".super ").append(superName).append(NL);

        for (Field field : ollirResult.getOllirClass().getFields()) {
            code.append(generators.apply(field));
        }
        code.append(NL);
        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload 0
                    invokespecial %s.<init>()V
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
                method.getMethodAccessModifier().name().toLowerCase() + (method.isStaticMethod() ? " static" : "") + " " :
                "";

        var methodName = method.getMethodName();
        code.append("\n.method ").append(modifier).append(methodName).append("(");

        for (Element param : method.getParams()) {
            Type type = param.getType();
            code.append(ollirTypeToJasminType(type));
        }

        String returnType = ollirTypeToJasminType(method.getReturnType());
        code.append(")").append(returnType).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (Instruction inst : method.getInstructions()) {
            String instCode = StringLines.getLines(generators.apply(inst)).stream().collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);

            if (inst instanceof CallInstruction callInstruction && !callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
                code.append(TAB + "pop" + NL);
            }
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }


        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        String storeInstruction = switch(operand.getType().getTypeOfElement()){
            case INT32, BOOLEAN -> "istore ";
            case ARRAYREF, OBJECTREF, CLASS, STRING -> "astore ";
            case THIS, VOID -> null;
        };
        code.append(storeInstruction).append(reg).append(NL);
        return code.toString();
    }


    private String generateField(Field field){
        var code = new StringBuilder();
        String accessModifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT ?
                field.getFieldAccessModifier().name().toLowerCase() + " " :
                "private ";

        String fieldName = field.getFieldName();

        String jasminType = ollirTypeToJasminType(field.getFieldType());

        code.append("\n.field ").append(accessModifier).append(fieldName).append(" ").append(jasminType).append(NL);

        return code.toString();
    }



    private String generatePutFieldInstruction(PutFieldInstruction putFieldInst) {
        var code = new StringBuilder();
        Operand field = putFieldInst.getField();
        var reg = field.getParamId();
        code.append("aload ").append(reg).append(NL);

        code.append(generators.apply(putFieldInst.getValue()));

        code.append("putfield ");
        code.append(currentMethod.getOllirClass().getClassName()).append("/").append(field.getName()).append(" ");

        code.append(ollirTypeToJasminType(field.getType())).append(NL);

        return code.toString();
    }


    private String generateGetFieldInstruction(GetFieldInstruction getFieldInst) {
        var code = new StringBuilder();

        // Get field details
        var field = getFieldInst.getField();
        var reg = field.getParamId();
        currentMethod.getVarTable().get(field.getName()).setVirtualReg(reg);

        var fieldName = field.getName();
        var fieldType = ollirTypeToJasminType(field.getType());

        code.append("aload ").append(reg).append(NL);
        code.append("getfield ").append(currentMethod.getOllirClass().getClassName())
                .append("/").append(fieldName).append(" ").append(fieldType).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        return switch (operand.getType().getTypeOfElement()) {
            case BOOLEAN, INT32 -> "iload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
            default -> "aload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;

        };
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
            case SUB -> "isub";
            case DIV -> "idiv";
            case AND, ANDB -> "iand";
            case OR, ORB -> "ior";
            case EQ, NEQ, LTE, GTE -> "icmp";
            case NOT, NOTB -> "ineg";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // If the return is void, then .getOperand() is null, not sure how to handle this
        if (returnInst.hasReturnValue()){
            code.append(generators.apply(returnInst.getOperand()));
        }

        String returnCode = switch (returnInst.getReturnType().getTypeOfElement()){
            case INT32, BOOLEAN -> "ireturn";
            case ARRAYREF, OBJECTREF, CLASS, THIS, STRING -> "areturn";
            case VOID -> "return";
        };

        code.append(returnCode).append(NL);
        return code.toString();
    }

    private String generateCall(CallInstruction callInstruction) {
        StringBuilder code = new StringBuilder();
        String invocationCode = "";

        Operand caller = (Operand) callInstruction.getCaller();
        String callerName = caller.getName();

        ClassType callerClass = (ClassType) caller.getType();
        String callerType = getFullName(callerClass.getName());

        CallType invocationType = callInstruction.getInvocationType();

        ArrayList<String> argumentsType = new ArrayList<>();
        for (Element argument : callInstruction.getArguments()) {
            argumentsType.add(ollirTypeToJasminType(argument.getType()));
        }

        String returnType = ollirTypeToJasminType(callInstruction.getReturnType());
        switch (invocationType) {
            case invokespecial:
                invocationCode = getCall(invocationType.toString(), callerType, "<init>", argumentsType, "V");
                break;
            case invokevirtual:
                String virtualMethod = ((LiteralElement) callInstruction.getMethodName()).getLiteral();
                String virtualMethodName = virtualMethod.substring(1, virtualMethod.length() - 1);
                invocationCode = getCall(invocationType.toString(), callerType, virtualMethodName, argumentsType, returnType);
                code.append(generators.apply(callInstruction.getCaller()));
                break;

            case invokestatic:
                String staticMethod = ((LiteralElement) callInstruction.getMethodName()).getLiteral();
                String staticMethodName = staticMethod.substring(1, staticMethod.length() - 1);
                invocationCode = getCall(invocationType.toString(), getFullName(callerName), staticMethodName, argumentsType, returnType);
                break;

            case NEW:
                code.append("new ").append(callerType).append(NL).append("dup");
                break;
        }

        for (Element argument : callInstruction.getArguments()) {
            code.append(generators.apply(argument));
        }
        code.append(invocationCode).append(NL);

        return code.toString();
    }

    private String getCall(String invocationType, String className, String methodName, List<String> argumentsType, String returnType) {
        StringBuilder code = new StringBuilder();
        code.append(invocationType).append(" ").append(className).append(".").append(methodName).append("(");
        for (String argumentType : argumentsType) {
            code.append(argumentType);
        }
        code.append(")").append(returnType);
        return code.toString();
    }
    private String ollirTypeToJasminType(Type type) {
        // This method should map OLLIR types to Jasmin type descriptors
        return switch (type.getTypeOfElement()) {
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
            case THIS -> null;
            case ARRAYREF -> "[Ljava/lang/String;";
            case OBJECTREF, CLASS -> "L" + getFullName(((ClassType) type).getName()) + ";";
            };
        }

    private String getFullName(String shortName) {
        for (String importName : ollirResult.getOllirClass().getImports()) {
            if (importName.endsWith(shortName)) {
                return importName.replace(".", "/");
            }
        }

        return shortName;
    }
}
