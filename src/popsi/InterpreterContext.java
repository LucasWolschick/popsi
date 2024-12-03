package popsi;

import java.util.*;
import popsi.ast.Ast.Function;
import popsi.ast.Ast.Rec;

public class InterpreterContext {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Function> functions = new HashMap<>();
    private final Map<String, Rec> records = new HashMap<>(); // Store record definitions

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public void addFunction(String name, Function function) {
        functions.put(name, function);
    }

    public Function getFunction(String name) {
        return functions.get(name);
    }

    public void addRecord(String name, Rec record) {
        records.put(name, record);
    }

    public Record getRec(String name) {
        return records.get(name);
    }
}
