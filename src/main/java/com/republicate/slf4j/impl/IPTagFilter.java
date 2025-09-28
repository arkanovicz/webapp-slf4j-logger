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
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import com.republicate.slf4j.impl.MDCStore;

/**
 * An automatic filter which fills the IP MDC tag on the current thread.
 * 
 * @author Claude Brisson
 */

@WebFilter
(
 filterName = "webapp-slf4j-logger-ip-filter",
 urlPatterns = { "/*" },
 dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD},
 displayName = "IP Tag Filter",
 description = "Sets the value of the IP Tag for the webapp-slf4j logger.",
 asyncSupported = true
)
public class IPTagFilter implements Filter
{
    private MDCStore mdcStore;

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        mdcStore = MDCStore.getSingleton();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws ServletException, IOException
    {
        if (req instanceof HttpServletRequest)
        {
            HttpServletRequest request = (HttpServletRequest)req;
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null)
            {
                ip = request.getRemoteAddr();
            }
            else
            {
                int coma = ip.indexOf(',');
                if (coma != -1)
                {
                    ip = ip.substring(0, coma).trim(); // keep the left-most IP address                                    
                }
            }
            mdcStore.put("ip", ip);
            String user = request.getRemoteUser();
            mdcStore.put("user", user);
        }
        chain.doFilter(req, res);
    }

    @Override
    public void destroy()
    {
    }
}
