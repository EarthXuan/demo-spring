package com.ex.spring.servlet;

import com.ex.spring.annotation.EXAutowire;
import com.ex.spring.annotation.EXController;
import com.ex.spring.annotation.EXRequestMapping;
import com.ex.spring.annotation.EXService;
import com.ex.spring.constant.Constant;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class EXDispatcherServlet extends HttpServlet{

    private static final long serialVersionUID=1L;

    //读取的配置文件
    private Properties properties=new Properties();

    //保存需要扫描到的类
    private List<String> classNames=new ArrayList<String>();

    //ioc容器
    private Map<String,Object> ioc=new HashMap<String, Object>();

    private Map<String,Method> handlerMapping=new HashMap<String, Method>();

    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(Constant.ConfigInfo.LOCATION));
        //2.扫描所有相关的类
        doScanner(properties.getProperty(Constant.ConfigInfo.SCANPACKAGE));
        //3.初始化所有相关类的实例，保存到IOC容器中
        doInstance();
        //4.依赖注入DI
        doAutowire();
        //5.构造HandlerMapping
        initHandlerMapping();
        //6.等待请求，匹配URL，定位方法，反射调用执行
        //调用doGet/doPost

    }

    /**
     * 首字母小写
     * @param str
     * @return
     */
    private String lowerFirstCase(String str){
        char []chars=str.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    private void initHandlerMapping() {
        if (ioc. isEmpty()){ return;}
        for (Map.Entry<String, Object> entry : ioc.entrySet()){
        Class<?> clazz = entry .getValue () .getClass() ;
        if(!clazz. isAnnotationPresent (EXController.class)){continue; }
        String baseUrl = "";
        if (clazz.isAnnotationPresent (EXRequestMapping. class)){
            EXRequestMapping requestMapping = clazz .getAnnotation (EXRequestMapping.class);
            baseUrl = requestMapping.value() ;
        }
        Method[] methods = clazz .getMethods() ;
        for (Method method : methods) {
            if (!method. isAnnotationPresent (EXRequestMapping.class)){ continue;}
            EXRequestMapping requestMapping=method.getAnnotation (EXRequestMapping.class);
            String url =("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
            handlerMapping.put (url, method) ;
            System. out. println("mapped " + url + "," + method) ;
        }
        }
    }

    private void doAutowire() {
        if(ioc.isEmpty()){return ;}
        for(Map.Entry<String, Object> entry:ioc.entrySet()){
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(EXAutowire.class)){return;};
                EXAutowire exAutowire=field.getAnnotation(EXAutowire.class);
                String beanName=exAutowire.value().trim();
                if("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {
        try {
            if(classNames.size()==0){return;}
            for(String className:classNames){
                Class<?> clazz=Class.forName(className);
                if(clazz.isAnnotationPresent(EXController.class)){
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(EXService.class)){
                    EXService exService= clazz.getAnnotation(EXService.class);
                        String beanName=exService.value();
                        //假如EXService没有值
                        if(!"".equals(beanName.trim())){
                            ioc.put(beanName,clazz.newInstance());
                        }else{
                            //clazz.getInterfaces()获取父接口
                            Class<?>[] interfaces=clazz.getInterfaces();
                            for(Class<?> inter:interfaces){
                                ioc.put(inter.getName(),clazz.newInstance());
                            }
                        }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doScanner(String packageName) {
        URL url=this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
        File dir=new File(url.getFile());
        for(File file:dir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            } else {
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }
    }

    private void doLoadConfig(String initParameter) {
        InputStream inputStream=null;
        try {
            inputStream=this.getClass().getClassLoader().getResourceAsStream(initParameter);
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(inputStream!=null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       doDispatch(req,resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (this.handlerMapping.isEmpty()) {return;}

        String url = req.getRequestURI();

        String contextPath =req.getContextPath();

        url =url.replace(contextPath, "").replaceAll("/+","/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404");
            return;
        }
        Map<String,String[]>params=req.getParameterMap();
        Method method=this.handlerMapping.get(url);
        //获取方法的参数列表
        Class<?>[] parameterTypes=method.getParameterTypes();
        //获取请求参数
        Map<String,String[]> parameterMap=req.getParameterMap();
        //保存参数值
        Object [] paramValues=new Object[parameterTypes.length];
        //方法的参数列表
        for(int i=0;i<parameterTypes.length;i++){
            //根据参数名称，做某些处理
            Class parameterType=parameterTypes[i];
            if(parameterType==HttpServletRequest.class){
                //参数类型已明确，这边强转类型
                paramValues[i]=req;
                continue;
            }else if(parameterType==HttpServletResponse.class){
                paramValues[i]=resp;
                continue;
            }else if(parameterType==String.class){
                for(Map.Entry<String,String[]>param:parameterMap.entrySet()){
                    String value=Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]","")
                            .replaceAll(",\\s",",");
                    paramValues[i]=value;
                }
            }
        }
        String beanName=lowerFirstCase(method.getDeclaringClass().getSimpleName());
        //利用反射机制来调用
        try {
            method.invoke(this.ioc.get(beanName),paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }
}
