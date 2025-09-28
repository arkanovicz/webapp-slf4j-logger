/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.republicate.slf4j.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.ServletContext;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

import com.republicate.slf4j.util.CachingDateFormatter;
import com.republicate.slf4j.util.MailNotifier;

/**
 * ServletContextLogger implementation.
 * 
 * @author Patrick Mahoney
 * @author Claude Brisson
 */

public final class ServletContextLogger extends MarkerIgnoringBase
{
    
    private static final long serialVersionUID = 3L;
    
    public static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    public static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    public static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    public static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    public static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;

    protected static final String INIT_PARAMETER_PREFIX = "webapp-slf4j-logger";
    protected static Pattern oldLogLevelInitParam = Pattern.compile("(\\S+)\\.level", Pattern.CASE_INSENSITIVE);
    protected static Pattern logLevelInitParam = Pattern.compile("level\\.(\\S+)", Pattern.CASE_INSENSITIVE);
    protected static Map<String, Level> explicitLevels = new HashMap<String, Level>();
    
    public enum Level
    {
        TRACE(LocationAwareLogger.TRACE_INT),
        DEBUG(LocationAwareLogger.DEBUG_INT),
        INFO(LocationAwareLogger.INFO_INT),
        WARN(LocationAwareLogger.WARN_INT),
        ERROR(LocationAwareLogger.ERROR_INT);
        
        private final int level;

        Level(int level) { this.level = level; }
        public int getValue() { return level; }
        public String toLowerCase()
        {
            if (level <= LocationAwareLogger.TRACE_INT)
            {
                return "trace";
            }
            else if (level <= LocationAwareLogger.DEBUG_INT)
            {
                return "debug";
            }
            else if (level <= LocationAwareLogger.INFO_INT)
            {
                return "info.";
            }
            else if (level <= LocationAwareLogger.WARN_INT)
            {
                return "warn.";
            }
            else /* if (level <= LocationAwareLogger.ERROR_INT) */
            {
                return "error";
            }
        }
        public String toStandardCase()
        {
            if (level <= LocationAwareLogger.TRACE_INT)
            {
                return "Trace";
            }
            else if (level <= LocationAwareLogger.DEBUG_INT)
            {
                return "Debug";
            }
            else if (level <= LocationAwareLogger.INFO_INT)
            {
                return "Info.";
            }
            else if (level <= LocationAwareLogger.WARN_INT)
            {
                return "Warn.";
            }
            else /* if (level <= LocationAwareLogger.ERROR_INT) */
            {
                return "Error";
            }
        }
        public String toUpperCase()
        {
            if (level <= LocationAwareLogger.TRACE_INT)
            {
                return "TRACE";
            }
            else if (level <= LocationAwareLogger.DEBUG_INT)
            {
                return "DEBUG";
            }
            else if (level <= LocationAwareLogger.INFO_INT)
            {
                return "INFO.";
            }
            else if (level <= LocationAwareLogger.WARN_INT)
            {
                return "WARN.";
            }
            else /* if (level <= LocationAwareLogger.ERROR_INT) */
            {
                return "ERROR";
            }
        }
    }

    public enum ElemType
    {
        DATE,
        LEVEL_LC,
        LEVEL_SC,
        LEVEL_UC,
        LOGGER,
        MESSAGE,
        CONTEXT, // MDC
        LITERAL,
        DECORATOR,
        SPACE
    }

    protected static Pattern splitter = Pattern.compile("%[a-zA-Z0-9]+|[^% ]+| +", Pattern.DOTALL);
    protected static Pattern decorator = Pattern.compile("[(\\[{)\\]}.@:,;/<>#-]+");
    protected static CachingDateFormatter dateFormat = new CachingDateFormatter("yyyy-MM-dd HH:mm:ss,SSS");
    protected static MDCStore mdcStore = MDCStore.getSingleton();

    public static class FormatElem
    {
        public ElemType type;
        public String content = null;
    }

    public static class Format
    {
        Format(String str)
        {
            List lst = new ArrayList();
            Matcher matcher = splitter.matcher(str);
            while (matcher.find())
            {
                FormatElem element = new FormatElem();
                lst.add(element);
                String elem = matcher.group();
                if (elem.startsWith("%"))
                {
                    if (elem.equals("%date"))
                    {
                        element.type = ElemType.DATE;
                    }
                    else if (elem.equals("%level"))
                    {
                        element.type = ElemType.LEVEL_LC;
                    }
                    else if (elem.equals("%Level"))
                    {
                        element.type = ElemType.LEVEL_SC;
                    }
                    else if (elem.equals("%LEVEL"))
                    {
                        element.type = ElemType.LEVEL_UC;
                    }
                    else if (elem.equals("%logger"))
                    {
                        element.type = ElemType.LOGGER;
                    }
                    else if (elem.equals("%message"))
                    {
                        element.type = ElemType.MESSAGE;
                    }
                    else
                    {
                        element.type = ElemType.CONTEXT;
                        element.content = elem.substring(1);
                    }
                }
                else if (elem.trim().length() == 0)
                {
                    element.type = ElemType.SPACE;
                    element.content = elem;
                }
                else
                {
                    Matcher m = decorator.matcher(elem);
                    element.type = m.matches() ? ElemType.DECORATOR : ElemType.LITERAL;
                    element.content = elem;
                }
            }
            elements = (FormatElem[])lst.toArray(new FormatElem[lst.size()]);
        }

        String layout(String logger, Level level, String message)
        {
            StringBuilder builder = new StringBuilder(128);
            int pos = 0;
            boolean mayDrop = true;
            boolean shouldDrop = false;
            for (FormatElem element : elements)
            {
                int prevPos = builder.length();
                boolean mayDropElem = false;
                switch (element.type)
                {
                    case DATE:
                    {
                        builder.append(dateFormat.format(System.currentTimeMillis()));
                        break;
                    }
                    case LEVEL_LC:
                    {
                        builder.append(level.toLowerCase());
                        break;
                    }
                    case LEVEL_SC:
                    {
                        builder.append(level.toStandardCase());
                        break;
                    }
                    case LEVEL_UC:
                    {
                        builder.append(level.toUpperCase());
                        break;
                    }
                    case LOGGER:
                    {
                        builder.append(logger);
                        break;
                    }
                    case MESSAGE:
                    {
                        builder.append(message);
                        break;
                    }
                    case CONTEXT:
                    {
                        String fragment = mdcStore.get(element.content);
                        if (fragment != null)
                        {
                            builder.append(fragment);
                        }
                        else
                        {
                            mayDropElem = true;
                            shouldDrop = true;
                        }
                        break;
                    }
                    case LITERAL:
                    {
                        builder.append(element.content);
                        break;
                    }
                    case SPACE:
                    {
                        if (mayDrop && shouldDrop)
                        {
                            builder.delete(pos, Integer.MAX_VALUE);
                        }
                        builder.append(element.content);
                        pos = builder.length();
                        mayDrop = mayDropElem = true;
                        shouldDrop = false;
                        break;
                    }
                    case DECORATOR:
                    {
                        mayDropElem = true;
                        builder.append(element.content);
                        break;
                    }
                }

                mayDrop = (mayDrop && mayDropElem);
                if (!mayDrop)
                {
                    pos = builder.length();
                }
            }
            if (mayDrop && shouldDrop)
            {
                builder.delete(pos, Integer.MAX_VALUE);
            }
            return builder.toString();
        }
        private FormatElem elements[] = null;
    }
    
    // Servlet context
    private static ServletContext context = null;
    
    private static Level enabledLevel = Level.INFO;
    private Level loggerLevel = enabledLevel;

    private static String defaultFormat = "%logger [%level] [%ip] %message";
    private static Format format = new Format(defaultFormat);

    private static Level notificationLevel = Level.ERROR;
    private static MailNotifier mailNotifier = null;

    private static Throwable configurationError = null;

    private static StackTraceStripper stackTraceStripper = new StackTraceStripper();

    private static void configure(String initParameter, String value)
    {
        switch (initParameter)
        {
            case "level": enabledLevel = Level.valueOf(value.toUpperCase()); break;
            case "format": format = new Format(value); break;
            case "notification":
            {
                if ("false".equals(value))
                {
                    return;
                }
                String tokens[] = value.split(":");
                if (tokens.length != 3)
                {
                    throw new IllegalArgumentException("notifications: expecting 3 tokens: 'level:from_address:to_address'");
                }
                notificationLevel = Level.valueOf(tokens[0].toUpperCase());
                if (mailNotifier != null)
                {
                    throw new IllegalArgumentException("notifications: mailer has already been configured");
                }
                mailNotifier = MailNotifier.getInstance(tokens[1], tokens[2]);
                mailNotifier.start();
                break;
            }
            case "stripper":
            {
                if ("none".equals(value) || "false".equals(value) || value.isEmpty())
                {
                    stackTraceStripper = null;
                }
                else if ("default".equals(value))
                {
                    // nothing to do
                }
                else
                {
                    stackTraceStripper = new StackTraceStripper(value);
                }
                break;
            }
            default:
            {
                Matcher m = logLevelInitParam.matcher(initParameter);
                if (m.matches())
                {
                    String loggerName = m.group(1);
                    explicitLevels.put(loggerName, Level.valueOf(value.toUpperCase()));
                }
                else
                {
                    m = oldLogLevelInitParam.matcher(initParameter);
                    if (m.matches())
                    {
                        String loggerName = m.group(1);
                        explicitLevels.put(loggerName, Level.valueOf(value.toUpperCase()));
                    }
                    else
                    {
                        throw new IllegalArgumentException("invalid init parameter name: " + initParameter); // let's stay pedantic here
                    }
                }
            }
        }

    }

    /**
     * <p>Set the ServletContext used by all ServletContextLogger objects.  This
     * should be done in a ServletContextListener, e.g. ServletContextLoggerSCL.</p>
     * <p>Configuration is read:</p>
     * <ul>
     *     <li>from /WEB-INF/web.xml init parameters, prefixed by the configuration namespace</li>
     *     <li>from /WEB-INF/logger.properties</li>
     *     <li>from Java system properties, prefixed by the configuration namespace</li>
     * </ul>
     * <p>The latter has the precedence, since it will override the former.</p>
     * <p>The configuration namespace is <code>webapp-slf4j-logger</code></p>
     * 
     * @param ctx servlet context
     */
    public static void setServletContext(ServletContext ctx)
    {
        context = ctx;
        if (context != null)
        {
            try
            {
                for (Enumeration<String> initParameters = context.getInitParameterNames(); initParameters.hasMoreElements();)
                {
                    String initParameter = initParameters.nextElement();
                    if (!initParameter.startsWith(INIT_PARAMETER_PREFIX + '.')) continue;
                    String value = context.getInitParameter(initParameter);
                    initParameter = initParameter.substring(INIT_PARAMETER_PREFIX.length() + 1);
                    configure(initParameter, value);
                }
                InputStream fileConfig = ctx.getResourceAsStream("/WEB-INF/logger.properties");
                if (fileConfig != null)
                {
                    Properties properties = new Properties();
                    properties.load(fileConfig);
                    for (Enumeration<String> keys = (Enumeration<String>)properties.propertyNames(); keys.hasMoreElements();)
                    {
                        String initParameter = keys.nextElement();
                        String value = properties.getProperty(initParameter);
                        configure(initParameter, value);
                    }
                }
                for (String prop: System.getProperties().stringPropertyNames()) {
                    if (!prop.startsWith(INIT_PARAMETER_PREFIX + '.')) continue;
                    String value = System.getProperty(prop);
                    prop = prop.substring(INIT_PARAMETER_PREFIX.length() + 1);
                    configure(prop, value);
                }
            }
            catch (Throwable t)
            {
                configurationError = t;
            }
        }
        else
        {
            if (mailNotifier != null && mailNotifier.isRunning())
            {
                mailNotifier.stop();
            }
        }
    }
    
    public static ServletContext getServletContext()
    {
        return context;
    }
    
    /**
     * Package access allows only to instantiate
     * ServletContextLogger instances.
     */
    ServletContextLogger(String name)
    {
        this.name = name;
        Level level = explicitLevels.get(name);
        while (level == null)
        {
            int dot = name.lastIndexOf('.');
            if (dot == -1) break;
            else
            {
                name = name.substring(0, dot);
                level = explicitLevels.get(name);
            }
        }
        if (level != null)
        {
            loggerLevel = level;
        }
    }
    
    /**
     * Is the given log level currently enabled?
     *
     * @param level target level
     * @return whether the level is enabled
     */
    protected boolean isLevelEnabled(Level level)
    {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (context != null && level.getValue() >= loggerLevel.getValue());
    }

    /**
     * Does the given log level trigger a notification?
     *
     * @param level target level
     * @return whether the level does trigger a notification
     */
    protected boolean triggersNotification(Level level)
    {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (context != null && mailNotifier != null && level.getValue() >= notificationLevel.getValue() && mailNotifier.isRunning());
    }

    private void log(Level level, String message, Throwable t)
    {
        if (!isLevelEnabled(level)) return;
        final ServletContext context = getServletContext();
        if (context != null)
        {
            // log any configuration error at the first received log request
            if (configurationError != null)
            {
                synchronized(this)
                {
                    Throwable e = configurationError;
                    configurationError = null;
                    log(Level.ERROR, "webapp-slf4j-logger configuration error", e);
                }
            }
            String formatted = format.layout(name, level, message);
            if (t == null)
            {
                context.log(formatted);
            }
            else
            {
                if (stackTraceStripper != null)
                {
                    stackTraceStripper.strip(t);
                }
                context.log(formatted, t);
            }
            if (triggersNotification(level))
            {
                String subject = formatted;
                int cr = subject.indexOf('\n');
                if (cr != -1) subject = subject.substring(0, cr);
                StringBuilder body = new StringBuilder();
                body.append(formatted);
                if (t != null)
                {
                    StringWriter stack = new StringWriter();
                    t.printStackTrace(new PrintWriter(stack));
                    body.append(stack.toString());
                }
                try
                {
                    mailNotifier.sendNotification(subject, body.toString());
                }
                catch (IOException ioe)
                {
                    context.log("[webapp-slf4j-logger] could not send notification email: " + ioe.getMessage());
                }
            }
        }
    }
    
    private void log(Level level, String message)
    {
        log(level, message, null);
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arg1
     * @param arg2
     */
    private void formatAndLog(Level level, String format, Object arg1, Object arg2)
    {
        if (!isLevelEnabled(level))
        {
            return;
        }
      
        final FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arguments a list of 3 ore more arguments
     */
    private void formatAndLog(Level level, String format, Object... arguments)
    {
        if (!isLevelEnabled(level))
        {
            return;
        }
      
        final FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    public void debug(String arg0)
    {
        log(Level.DEBUG, arg0);
    }

    public void debug(String arg0, Object arg1)
    {
        formatAndLog(Level.DEBUG, arg0, arg1);
    }

    public void debug(String arg0, Object[] arg1)
    {
        formatAndLog(Level.DEBUG, arg0, arg1);
    }

    public void debug(String arg0, Throwable arg1)
    {
        formatAndLog(Level.DEBUG, arg0, arg1);
    }

    public void debug(String arg0, Object arg1, Object arg2)
    {
        formatAndLog(Level.DEBUG, arg0, arg1, arg2);
    }

    public void error(String arg0)
    {
        log(Level.ERROR, arg0);
    }

    public void error(String arg0, Object arg1)
    {
        formatAndLog(Level.ERROR, arg0, arg1);
    }

    public void error(String arg0, Object[] arg1)
    {
        formatAndLog(Level.ERROR, arg0, arg1);
    }

    public void error(String arg0, Throwable arg1)
    {
        formatAndLog(Level.ERROR, arg0, arg1);
    }

    public void error(String arg0, Object arg1, Object arg2)
    {
        formatAndLog(Level.ERROR, arg0, arg1, arg2);
    }

    public void info(String arg0)
    {
        log(Level.INFO, arg0);
    }
    public void info(String arg0, Object arg1)
    {
        formatAndLog(Level.INFO, arg0, arg1);
    }

    public void info(String arg0, Object[] arg1)
    {
        formatAndLog(Level.INFO, arg0, arg1);
    }

    public void info(String arg0, Throwable arg1)
    {
        formatAndLog(Level.INFO, arg0, arg1);
    }

    public void info(String arg0, Object arg1, Object arg2)
    {
        formatAndLog(Level.INFO, arg0, arg1, arg2);
    }

    public boolean isDebugEnabled()
    {
        return isLevelEnabled(Level.DEBUG);
    }

    public boolean isErrorEnabled()
    {
        return isLevelEnabled(Level.ERROR);
    }

    public boolean isInfoEnabled()
    {
        return isLevelEnabled(Level.INFO);
    }

    public boolean isTraceEnabled()
    {
        return isLevelEnabled(Level.TRACE);
    }

    public boolean isWarnEnabled()
    {
        return isLevelEnabled(Level.WARN);
    }

    public void trace(String arg0)
    {
        log(Level.TRACE, arg0);
    }

    public void trace(String arg0, Object arg1)
    {
        formatAndLog(Level.TRACE, arg0, arg1);
    }

    public void trace(String arg0, Object[] arg1)
    {
        formatAndLog(Level.TRACE, arg0, arg1);
    }

    public void trace(String arg0, Throwable arg1)
    {
        formatAndLog(Level.TRACE, arg0, arg1);
    }

    public void trace(String arg0, Object arg1, Object arg2)
    {
        formatAndLog(Level.TRACE, arg0, arg1, arg2);
    }

    public void warn(String arg0)
    {
        log(Level.WARN, arg0);
    }

    public void warn(String arg0, Object arg1)
    {
        formatAndLog(Level.WARN, arg0, arg1);
    }

    public void warn(String arg0, Object[] arg1)
    {
        formatAndLog(Level.WARN, arg0, arg1);
    }

    public void warn(String arg0, Throwable arg1)
    {
        formatAndLog(Level.WARN, arg0, arg1);
    }

    public void warn(String arg0, Object arg1, Object arg2)
    {
        formatAndLog(Level.WARN, arg0, arg1, arg2);
    }
}
