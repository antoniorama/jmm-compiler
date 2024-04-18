package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        generators.put(Field.class, this::generateField);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
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

        for (Field field : ollirResult.getOllirClass().getFields()) {
            code.append(generators.apply(field));
        }
        code.append(NL);
        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload 0
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
            params.append(ollirTypeToJasminType(p.getType()));
        }
        String returnType = ollirTypeToJasminType(method.getReturnType());

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
        String acessModifier = getJasminAccessModifier(field.getFieldAccessModifier());

        String fieldName = field.getFieldName();

        String jasminType = ollirTypeToJasminType(field.getFieldType());

        code.append(".field ").append(acessModifier).append(" ").append(fieldName).append(" ").append(jasminType).append("\n");

        return code.toString();
    }

    private String getJasminAccessModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            default -> "";  // default is package-private, which does not have a keyword in Jasmin
        };
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInst) {
        var code = new StringBuilder();
        Operand field =putFieldInst.getField();
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
        var fieldName = field.getName();
        var fieldType = ollirTypeToJasminType(field.getType());

        code.append("aload ").append(field.getParamId()).append(NL);
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
            case THIS, STRING, ARRAYREF, OBJECTREF -> "aload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
            case BOOLEAN, INT32 -> "iload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg()+ NL;
            default -> null;
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

    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();

        // Check if it's a constructor call
        var className = ollirResult.getOllirClass().getClassName();
        if (call.getInvocationType() == CallType.NEW) {
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL);

            code.append("invokespecial ").append(className).append("/<init>()V").append(NL);
            // code.append("pop").append(NL);
        }
        else if (call.getInvocationType() == CallType.invokestatic) {
            String callerName = extractClassName(call.getCaller().toElement().toString());
            String methodName = extractMethodName(call.getMethodName().toElement().toString());
            String operandString = ollirTypeToJasminType(call.getReturnType());

            List<Element> args = call.getArguments();

            StringBuilder argTypes = new StringBuilder();
            for (Element arg : args) {
                String argType = ollirTypeToJasminType(arg.getType());
                argTypes.append(argType);
                code.append(generateArgument(arg));
            }

            code.append("invokestatic ").append(callerName).append("/").append(methodName).append("(").append(argTypes).append(")").append(operandString).append(NL);
        }
        else if (call.getInvocationType() == CallType.invokevirtual) {
            String callerName = extractClassType(call.getCaller().toElement().toString());
            String callerName2 = extractClassName(call.getCaller().toElement().toString()); // needed for getting aload

            /*
            System.out.println("\nDEBUG INVOKEVIRTUAL");
            System.out.println("CALLERNAME2 : " + callerName2);
            System.out.println("VAR TABLE : " + currentMethod.getVarTable());
            System.out.println("\n");
             */

            var virtualReg = currentMethod.getVarTable().get(callerName2).getVirtualReg();
            code.append("aload ").append(virtualReg).append(NL);

            String methodName = extractMethodName(call.getMethodName().toElement().toString());
            String operandString = ollirTypeToJasminType(call.getReturnType());

            List<Element> args = call.getArguments();

            StringBuilder argTypes = new StringBuilder();
            for (Element arg : args) {
                String argType = ollirTypeToJasminType(arg.getType());
                argTypes.append(argType);
                code.append(generateArgument(arg));
            }

            code.append("invokevirtual ").append(callerName).append("/").append(methodName).append("(").append(argTypes).append(")").append(operandString).append(NL);
        }

        return code.toString();
    }

    private String ollirTypeToJasminType(Type type) {
        // This method should map OLLIR types to Jasmin type descriptors
        return switch (type.toString()) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "STRING[]" -> "[Ljava/lang/String;";
            case "VOID" -> "V";
            default -> {
                if (type.toString().startsWith("OBJECTREF")) {
                    // Assuming the format is OBJECTREF(<ClassName>)
                    var className = type.toString().substring(9, type.toString().length() - 1);
                    yield "L" + className + ";";
                } else {
                    throw new NotImplementedException("Jasmin type not implemented for: " + type);
                }
            }
        };
    }

    private String extractClassName(String callRepresentation) {
        // This method parses the class name from the call representation
        String classNamePattern = "Operand: (.+?)\\.";
        return matchPattern(callRepresentation, classNamePattern);
    }

    private String extractClassType(String callRepresentation) {
        String classTypePattern = "\\((.*?)\\)";
        return matchPattern(callRepresentation, classTypePattern);
    }

    private String extractMethodName(String callRepresentation) {
        // This method parses the method name from the call representation
        String methodNamePattern = "LiteralElement: \"(.+?)\"\\.STRING";
        return matchPattern(callRepresentation, methodNamePattern);
    }

    private String matchPattern(String input, String regexPattern) {
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1); // Return the first capturing group
        }
        return ""; // Return an empty string if no match was found
    }

    private String generateArgument(Element arg) {
        if (arg instanceof LiteralElement) {
            return "ldc " + ((LiteralElement) arg).getLiteral() + NL;
        } else if (arg instanceof Operand operand) {
            int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
            return loadInstructionForType(operand.getType()) + " " + reg + NL;
        }
        return "";
    }

    private String loadInstructionForType(Type type) {
        switch (type.toString()) {
            case "INT32", "BOOLEAN" -> {
                return "iload ";
            }
            default -> {
                return "aload ";
            }
        }
    }
}
