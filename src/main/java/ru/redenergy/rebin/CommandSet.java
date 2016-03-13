package ru.redenergy.rebin;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.config.ConfigCategory;
import ru.redenergy.rebin.annotation.Arg;
import ru.redenergy.rebin.annotation.Command;
import ru.redenergy.rebin.resolve.ResolveResult;
import ru.redenergy.rebin.resolve.TemplateResolver;
import ru.skymine.permissions.Permissions;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class CommandSet extends CommandBase {

    //TODO: Read message from external file
    public static String NO_PERMISSION_MSG = EnumChatFormatting.RED + "\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432\u0021";

    private final TemplateResolver resolver = new TemplateResolver();
    private List<CommandConfiguration> configs = new ArrayList<>();

    public void collectCommands(){
        Method[] methods = this.getClass().getMethods();
        for(Method m : methods) {
            Command command = m.getAnnotation(Command.class);
            if(command != null) configs.add(new CommandConfiguration(m, m.getParameterTypes(), m.getParameterAnnotations()));
        }
    }

    private void resolveAndInvoke(ICommandSender sender, String[] args) throws InvocationTargetException, IllegalAccessException {
        for(CommandConfiguration configuration : configs){
            String template = configuration.getCommandMethod().getAnnotation(Command.class).value();
            ResolveResult result = resolver.resolve(template, args);
            if(result.isSuccess()) {
                Command command = configuration.getCommandMethod().getAnnotation(Command.class);
                if(command.permission().equals("#") || sender instanceof MinecraftServer || Permissions.hasPermission(sender.getCommandSenderName(), command.permission())) {
                    invokeCommand(configuration, result.getArguments(), sender, args);
                } else {
                    sender.addChatMessage(new ChatComponentText(NO_PERMISSION_MSG));
                }
            }
        }
    }
    
    private void invokeCommand(CommandConfiguration command, Map<String, String> extracted, ICommandSender sender, String[] args) throws InvocationTargetException, IllegalAccessException {
        command.getCommandMethod().invoke(this, commandArguments(command, sender, extracted));
    }

    private Object[] commandArguments(CommandConfiguration command, ICommandSender sender, Map<String, String> args){
        Object[] parameters = new Object[command.getParameters().length];
        for(int i = 0; i < command.getParameters().length; i++){
            Class clazz = command.getParameters()[i];
            if(clazz.isAssignableFrom(sender.getClass())){
                parameters[i] = sender;
            } else {
                Arg arg = findArgumentAnnotation(command.getAnnotations()[i]); // <|*|>
                if(arg == null){
                    throw new RuntimeException("Unable to resolve arguments for " + command.getCommandMethod());
                }
                parameters[i] = args.get(arg.value());
            }
        }
        return parameters;
    }

    private Arg findArgumentAnnotation(Annotation[] annotations){
        for(Annotation annotation : annotations){
            if(annotation.annotationType().isAssignableFrom(Arg.class)){
                return (Arg) annotation;
            }
        }
        return null;
    }



    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            resolveAndInvoke(sender, args);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void register(CommandSet set){
        set.collectCommands();
        ((CommandHandler)MinecraftServer.getServer().getCommandManager()).registerCommand(set);
    }

}