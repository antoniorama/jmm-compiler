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
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstruction);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(Field.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondInstruction);
        generators.put(GotoInstruction.class, this::generateGoToInstruction);
        generators.put(OpCondInstruction.class, this::generateOpCondInstruction);
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
        StringBuilder methodBody = new StringBuilder();

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
        //System.out.println(((ArrayType) method.getReturnType()).getElementType().toString());
        String returnType = ollirTypeToJasminType(method.getReturnType());
        code.append(")").append(returnType).append(NL);

        for (Instruction inst : method.getInstructions()) {
            List<String> label = method.getLabels(inst);

            if (label != null) {
                for (String l : label) methodBody.append(l).append(":").append(NL);
            }

            String instCode = StringLines.getLines(generators.apply(inst)).stream().collect(Collectors.joining(NL + TAB, TAB, NL));

            methodBody.append(instCode);

            /*
            if (inst instanceof CallInstruction callInstruction && !callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
                code.append(TAB + "pop" + NL);
            }
             */
        }

        var varTable = method.getVarTable();
        int numOfVars = varTable.size();

        code.append(".limit stack ").append(getStackLimit(methodBody.toString())).append(NL);
        code.append(".limit locals ").append(numOfVars + 1).append(NL);
        code.append(methodBody);
        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private int getStackLimit(String jasminCode) {
        int currentStack = 0;
        int maxStack = 0;

        // Split the input code into individual lines/instructions
        String[] lines = jasminCode.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            if (trimmedLine.startsWith("aload") || trimmedLine.startsWith("iload") || trimmedLine.startsWith("fload") || trimmedLine.startsWith("dload") ||
                    trimmedLine.startsWith("ldc") || trimmedLine.startsWith("new") || trimmedLine.startsWith("dup")) {
                currentStack += 1;
            } else if (trimmedLine.startsWith("istore") || trimmedLine.startsWith("fstore") || trimmedLine.startsWith("dstore") ||
                    trimmedLine.startsWith("astore") || trimmedLine.startsWith("pop")) {
                currentStack -= 1;
            } else if (trimmedLine.startsWith("iadd") || trimmedLine.startsWith("isub") || trimmedLine.startsWith("imul") || trimmedLine.startsWith("idiv") ||
                    trimmedLine.startsWith("fadd") || trimmedLine.startsWith("fsub") || trimmedLine.startsWith("fmul") || trimmedLine.startsWith("fdiv")) {
                currentStack -= 1;
            } else if (trimmedLine.startsWith("invokevirtual") || trimmedLine.startsWith("invokestatic") || trimmedLine.startsWith("invokespecial")) {
                int numArgs = countMethodArgs(trimmedLine);
                currentStack -= numArgs;
                if (!methodReturnsVoid(trimmedLine)) {
                    currentStack += 1;
                }
            } else if (trimmedLine.startsWith("ireturn") || trimmedLine.startsWith("freturn") || trimmedLine.startsWith("dreturn") ||
                    trimmedLine.startsWith("areturn")) {
                currentStack -= 1;
            }

            if (currentStack > maxStack) {
                maxStack = currentStack;
            }
        }

        return maxStack;
    }

    private int countMethodArgs(String invokeInstruction) {
        String descriptor = invokeInstruction.substring(invokeInstruction.indexOf("(") + 1, invokeInstruction.indexOf(")"));
        return (int) descriptor.chars().filter(ch -> ch == 'I' || ch == 'F' || ch == 'D' || ch == 'L').count();
    }

    private boolean methodReturnsVoid(String invokeInstruction) {
        return invokeInstruction.contains(")V");
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

        /*var fieldName = field.getName();
        var fieldType = ollirTypeToJasminType(field.getType());

        code.append("aload ").append(reg).append(NL);
        code.append("getfield ").append(currentMethod.getOllirClass().getClassName())
                .append("/").append(fieldName).append(" ").append(fieldType).append(NL);
        */

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

    private String generateArrayOperand(ArrayOperand arrOp){
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(arrOp.getName()).getVirtualReg();
        code.append("aload").append(reg).append(NL);
        code.append(generators.apply(arrOp.getIndexOperands().get(0))).append(NL).append("iaload");
        return code.toString();
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

    private String generateUnaryOpInstruction(UnaryOpInstruction unaryOp){
        var code = new StringBuilder();
        code.append("ldc 1").append(NL);
        code.append(generators.apply(unaryOp.getOperand()));

        code.append("ixor").append(NL);

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

        String callerType = "";

        if (caller.getType() instanceof ClassType){
            ClassType callerClass = (ClassType) caller.getType();
            callerType = getFullName(callerClass.getName());
        }
        else if (caller.getType() instanceof ArrayType) {
            callerType = "int";
        }

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
                /*
                if (callerType.equals("int")) {
                    //TODO need to change type utils or kind to recognize ARRAYOPERAND probablyy
                    for (Element element : callInstruction.getOperands()) {
                        if (element instanceof ArrayOperand arrayOperand) {
                            code.append(generators.apply(arrayOperand));
                        } else {
                            throw new ClassCastException("Expected ArrayOperand but got " + element.getClass().getSimpleName());
                        }
                    }
                    code.append("newarray int").append(NL);
                } else {
                    code.append("new ").append(callerType).append(NL).append("dup").append(NL);
                }*/
                code.append("new ").append(callerType).append(NL).append("dup").append(NL);
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
            case ARRAYREF -> ((ArrayType) type).getElementType().toString().equals("INT32") ? "[INT32" : "[Ljava/lang/String;" ;
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

    // TODO
    private String generateSingleOpCondInstruction(SingleOpCondInstruction instruction) {
        StringBuilder code = new StringBuilder();

        String condCode = StringLines.getLines(generators.apply(instruction.getCondition())).stream().collect(Collectors.joining(NL, TAB, NL));
        code.append(condCode);
        code.append("ifne ").append(instruction.getLabel());

        return code.toString();
    }

    // TODO
    private String generateGoToInstruction(GotoInstruction instruction) {
        return "goto " + instruction.getLabel() + NL;
    }

    private String generateOpCondInstruction(OpCondInstruction instruction) {
        StringBuilder code = new StringBuilder();

        // Generate code for both sides of the operation
        code.append(generators.apply(instruction.getOperands().get(0)));
        code.append(generators.apply(instruction.getOperands().get(1)));

        OperationType opType = instruction.getCondition().getOperation().getOpType();
        System.out.println("opType " + opType);

        String jumpInstruction = switch (opType) {
            case LTH -> "if_icmplt ";
            case GTH -> "if_icmpgt ";
            case LTE -> "if_icmple ";
            case GTE -> "if_icmpge ";
            case EQ -> "if_icmpeq ";
            case NEQ -> "if_icmpne ";
            default -> throw new UnsupportedOperationException("Operation type " + opType + " not supported.");
        };


        code.append(jumpInstruction).append(instruction.getLabel()).append(NL);

        return code.toString();
    }
}
