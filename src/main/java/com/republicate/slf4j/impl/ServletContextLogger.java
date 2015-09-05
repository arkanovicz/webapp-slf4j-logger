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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

/**
 * ServletContextLogger implementation.
 * 
 * @author Patrick Mahoney
 * @author Claude Brisson
 */


public final class ServletContextLogger extends MarkerIgnoringBase
{
    
    private static final long serialVersionUID = 2L;
    
    public static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    public static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    public static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    public static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    public static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;
    
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
        LITERAL
    }

    protected static Pattern splitter = Pattern.compile("(?:(%[a-zA-Z0-9]+)|((?!%).+))*");
    protected static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    protected static MDCStore mdcStore = MDCStore.getSingleton();

    public class Format
    {
        Format(String str)
        {
            Matcher matcher = splitter.matcher(str);
            if (matcher.matches())
            {
                count = matcher.groupCount();
                element = new ElemType[count];
                content = new String[count];
                for (int group = 0; group < count; ++group)
                {
                    element[group] = ElemType.LITERAL;
                    content[group] = "";
                    String elem = matcher.group(group - 1);
                    if (elem.startsWith("%"))
                    {
                        if (elem.equals("%date"))
                        {
                            element[group] = ElemType.DATE;
                        }
                        else if (elem.equals("%level"))
                        {
                            element[group] = ElemType.LEVEL_LC;
                        }
                        else if (elem.equals("%Level"))
                        {
                            element[group] = ElemType.LEVEL_SC;
                        }
                        else if (elem.equals("%LEVEL"))
                        {
                            element[group] = ElemType.LEVEL_UC;
                        }
                        else if (elem.equals("%logger"))
                        {
                            element[group] = ElemType.LOGGER;
                        }
                        else if (elem.equals("%message"))
                        {
                            element[group] = ElemType.MESSAGE;
                        }
                        else
                        {
                            element[group] = ElemType.CONTEXT;
                            content[group] = elem.substring(1);
                        }
                    }
                    else
                    {
                        element[group] = ElemType.LITERAL;
                        content[group] = elem;
                    }
                }
            }
        }
        String layout(Level level, String message)
        {
            StringBuilder builder = new StringBuilder(128);
            for (int i = 0; i < count; ++i)
            {
                switch (element[i])
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
                        builder.append(simpleName);
                        break;
                    }
                    case MESSAGE:
                    {
                        builder.append(message);
                        break;
                    }
                    case CONTEXT:
                    {
                        String fragment = mdcStore.get(content[i]);
                        if (fragment != null)
                        {
                            builder.append(fragment);
                        }
                        break;
                    }
                    case LITERAL:
                    {
                        builder.append(content[i]);
                        break;
                    }
                }
            }
            return builder.toString();
        }
        private int count = 0;
        private ElemType element[] = null;
        private String content[] = null;
    }
    
    // Servlet context
    private static ServletContext context = null;
    
    private static Level enabledLevel = Level.INFO;

    private static String defaultFormat = "%date [%level] [%ip] %message\n";

    /**
     * Set the ServletContext used by all ServletContextLogger objects.  This
     * should be done in a ServletContextListener, e.g. ServletContextLoggerSCL.
     * 
     * @param context
     */
    public static void setServletContext(ServletContext context)
    {
        context = context;
        if (context != null)
        {
            final String defaultLevel = context.getInitParameter("ServletContextLogger.LEVEL");
            if (defaultLevel != null)
            {
                enabledLevel = Level.valueOf(defaultLevel.toUpperCase());
            }

            final String givenFormat = context.getInitParameter("ServletContextLogger.FORMAT");
            if (givenFormat != null)
            {
                enabledLevel = Level.valueOf(defaultLevel.toUpperCase());
            }
        }
    }
    
    public static ServletContext getServletContext()
    {
        return context;
    }
    
    private final String name;    
    private final String simpleName;
    private final Format format = new Format(defaultFormat);
    
    /**
     * Package access allows only {@link SimpleLoggerFactory} to instantiate
     * SimpleLogger instances.
     */
    ServletContextLogger(String name)
    {
        this.name = name;
        final int dot;
        if ((dot = name.lastIndexOf(".")) != -1) simpleName = name.substring(dot+1);
        else simpleName = name;
    }
    
    /**
     * Is the given log level currently enabled?
     *
     * @param level is this level enabled?
     */
    protected boolean isLevelEnabled(Level level)
    {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (context != null && level.getValue() >= enabledLevel.getValue());
    }

    private void log(Level level, String message, Throwable t)
    {
        if (!isLevelEnabled(level)) return;
        final ServletContext context = getServletContext();
        if (context != null)
        {
            String formatted = format.layout(level, message);
            if (t == null)
            {
                context.log(formatted);
            }
            else
            {
                context.log(formatted, t);
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
