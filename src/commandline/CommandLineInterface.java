package commandline;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CommandLineInterface {
    private final Map<String, Method> commands;

    public CommandLineInterface() {
        this.commands = CommandLineInterface.enumerateCommands(this.getClass());
    }

    public static Map<String, Method> enumerateCommands(Class<? extends CommandLineInterface> kind) {
        var commands = new HashMap<String, Method>();
        Class<?> type = kind;
        while (type != Object.class) {
            for (var method: type.getDeclaredMethods()) {
                Command annotation = method.getAnnotation(Command.class);
                if (annotation == null || commands.containsKey(annotation.value()))
                    continue;
                commands.put(annotation.value(), method);
            }
            type = type.getSuperclass();
        }

        return Collections.unmodifiableMap(commands);
    }
}
