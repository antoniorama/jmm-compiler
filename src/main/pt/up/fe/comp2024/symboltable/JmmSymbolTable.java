package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmSymbolTable implements SymbolTable {

    private final String className;
    private final String extendedClass;
    private final List<String> methods;
    private final Map<String, Type> returnTypes; // methodName : returnType
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals; // methodName : Symbol of variable
    private final List<String> imports;
    private final List<Symbol> fields;

    public JmmSymbolTable(String className,
                          String extendedClass,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          List<String> imports,
                          List<Symbol> fields) {
        this.className = className;
        this.extendedClass = extendedClass;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.imports = imports;
        this.fields = fields;
    }

    @Override
    public List<String> getImports() {
        return this.imports;
    }

    @Override
    public String getClassName() {
        return this.className;
    }

    @Override
    public String getSuper() {return this.extendedClass;}

    @Override
    public List<Symbol> getFields() {return this.fields;}

    @Override
    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public Type getReturnType(String methodSignature) {return this.returnTypes.get(methodSignature);}

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return Collections.unmodifiableList(params.get(methodSignature));
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return Collections.unmodifiableList(locals.get(methodSignature));
    }

}
