package com.baidu.monitoring.util;


import javax.servlet.http.HttpServletRequest;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @program: commonUtils
 * @description: 公共工具类
 * @author: zhangyh
 * @create: 2022-04-07 13:31
 **/
public class CommonUtils {

    /**
     * 数据去重
     * @param str
     * @return
     */
    public static String[] checkArr(String[] str) {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < str.length; i++) {
            if (!list.contains(str[i])) {
                list.add(str[i]);
            }
        }
        // 返回一个包含所有对象的指定类型的数组
        String[] newStr = list.toArray(new String[1]);
        return newStr;
    }

    /**
     * <p>Title: compareFields</p>
     *
     * <p>Description: </p>
     * 比较两个实体属性值
     *
     * @param obj1
     * @param obj2
     * @param ignoreArr 忽略的字段
     * @return
     */
    public static Map<String, List<Object>> compareFields(Object obj1, Object obj2, String[] ignoreArr, String[] optionArr) {
        try {
            Map<String, List<Object>> map = new HashMap<String, List<Object>>();
            List<String> ignoreList = null;
            List<String> optionList = null;
            if (ignoreArr != null && ignoreArr.length > 0) {
                // array转化为list
                ignoreList = Arrays.asList(ignoreArr);
            }
            if (optionArr != null && optionArr.length > 0) {
                // array转化为list
                optionList = Arrays.asList(optionArr);
            }
            // 只有两个对象都是同一类型的才有可比性
            if (obj1.getClass() == obj2.getClass()) {
                Class clazz = obj1.getClass();
                // 获取object的属性描述
                PropertyDescriptor[] pds = Introspector.getBeanInfo(clazz,
                        Object.class).getPropertyDescriptors();
                // 这里就是所有的属性了
                for (PropertyDescriptor pd : pds) {
                    // 属性名
                    String name = pd.getName();
                    // 如果当前属性选择忽略比较，跳到下一次循环
                    if (ignoreList != null && ignoreList.contains(name)) {
                        continue;
                    }
                    // 如果当前设置属性不包含，跳到下一次循环
                    if (optionList != null && !optionList.contains(name)) {
                        continue;
                    }
                    // get方法
                    Method readMethod = pd.getReadMethod();
                    // 在obj1上调用get方法等同于获得obj1的属性值
                    Object o1 = readMethod.invoke(obj1);
                    // 在obj2上调用get方法等同于获得obj2的属性值
                    Object o2 = readMethod.invoke(obj2);
                    if (o1 instanceof Timestamp) {
                        o1 = new Date(((Timestamp) o1).getTime());
                    }
                    if (o2 instanceof Timestamp) {
                        o2 = new Date(((Timestamp) o2).getTime());
                    }
                    if (o1 == null && o2 == null) {
                        continue;
                    } else if (o1 == null && o2 != null) {
                        List<Object> list = new ArrayList<Object>();
                        list.add(o1);
                        list.add(o2);
                        map.put(name, list);
                        continue;
                    }
                    // 比较这两个值是否相等,不等就可以放入map了
                    if (!o1.equals(o2)) {
                        List<Object> list = new ArrayList<Object>();
                        list.add(o1);
                        list.add(o2);
                        map.put(name, list);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    /**
     * list转换成一个map
     * @param list
     * @param getKey
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, V> listConvertMap(Collection<V> list, Function<V, K> getKey) {
        return listConvertMap(list, getKey, Function.identity());
    }
    public static <K, V, R> Map<K, V> listConvertMap(Collection<R> list, Function<R, K> getKey, Function<R, V> getValue) {
        if (isEmpty(list)) {
            return new HashMap<>(8);
        }
        Map<K, V> map = new HashMap<>(Math.min(list.size(), 256));
        list.forEach(item -> map.put(getKey.apply(item), getValue.apply(item)));
        return map;
    }

    /**
     * 获取数组中的某个值，返回一个List
     * @param sourceList
     * @param getValue
     * @param <R>
     * @param <E>
     * @return
     */
    public static <R, E> List<R> getListByKey(List<E> sourceList, Function<E, R> getValue) {
        if (isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        List<R> rList = new ArrayList<>(sourceList.size());
        for (E item : sourceList) {
            R r = getValue.apply(item);
            if (Objects.nonNull(r)) {
                rList.add(r);
            }
        }
        return rList;
    }

    /**
     * 判决集合不为空
     * @param collection
     * @return
     */
    public static boolean isEmpty(Collection<?> collection) {
        return (collection == null || collection.isEmpty());
    }

    /**
     * 判断map是否为空
     * @param map
     * @return
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }


    /**
     * 合并两个数组
     * @param first
     * @param second
     * @param <T>
     * @return
     */
    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static <T> List<T> getMergeList(List<T> list1, List<T> list2,List<T> list3,List<T> list4,List<T> list5) {
        //return Stream.of(list1, list2).flatMap(x -> x.stream()).collect(Collectors.toList());
        return Stream.of(list1, list2,list3,list4,list5)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    public static <T> List<T> getMergeList(List<T> list) {
        //return Stream.of(list1, list2).flatMap(x -> x.stream()).collect(Collectors.toList());
        return Stream.of(list)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取一个 Map<K, List<V>>
     * @param list
     * @param getKey
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, List<V>> convertMapList(List<V> list, Function<V, K> getKey) {
        return convertMapList(list, getKey, Function.identity());
    }
    public static <K, V, R> Map<K, List<R>> convertMapList(List<V> list, Function<V, K> getKey,
                                                               Function<V, R> getValue) {
        if (list == null || list.isEmpty()) {
            return new HashMap<>(8);
        }
        Map<K, List<R>> map = new HashMap<>(Math.min(list.size(), 256));
        K key;
        for (V v : list) {
            key = getKey.apply(v);
            map.putIfAbsent(key, new ArrayList<>());
            R r = getValue.apply(v);
            map.get(key).add(r);
        }
        return map;
    }

    public static int isEmpty(int t1,int t2){
        if(Objects.nonNull(t1)){
            return t1;
        }else {
            return t2;
        }
    }

    public static double isEmpty(double t1,double t2){
        if(Objects.nonNull(t1)){
            return t1;
        }else {
            return t2;
        }
    }

    /**
     * 获取用户真实IP信息
     * @param request
     * @return
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }


    public static <T> T getEls(T value, T dev) {
        return isEmpty(value) ? dev : value;
    }
    public static boolean isEmpty(Object value) {
        return Objects.isNull(value);
    }

    public static String convertString(List<Long> list){
        String str = "";
        if(CommonUtils.isEmpty(list)){
            return str;
        }
        for (Long aLong : list) {
            str = str + "," + aLong;
        }
        return str.substring(1);
    }

    public static List<Long> convertList(String str){
        if(StringUtils.isBlank(str)){
            return new ArrayList<>();
        }
        String[] split = str.split(",");
        List<Long> longs = new ArrayList<>();
        for (String s : split) {
            if(StringUtils.isBlank(s)){
                continue;
            }
            longs.add(Long.parseLong(s));
        }
        return longs;
    }

    public static Set<Long> convertSet(String str){
        if(StringUtils.isBlank(str)){
            return null;
        }
        String[] split = str.split(",");
        Set<Long> longs = new TreeSet<>();
        for (String s : split) {
            longs.add(Long.parseLong(s));
        }
        return longs;
    }

    public static String randomCode(int length) {
        String code = "";
        Random random = new Random();
        for(int i = 0; i < length; i++) {
            code += random.nextInt(10);
        }
        return code;
    }

    /**
     * 百分比计算
     * @return
     */
    public static String intToPercent(int num1, int num2){
        if(num2 == 0){
            return "0.00";
        }
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        String result = numberFormat.format((float)num1/(float)num2*100);
        return result;
    }

    /**
     * 路径传参转化
     * @param pathQuery
     * @return
     */
    public static Map<String, String> formatUrlParams(String pathQuery) {
        Map<String, String> params = new HashMap<>();
        String[] queryList = pathQuery.split("&");
        for (String s : queryList){
            params.put(s.split("=")[0], s.split("=")[1]);
        }
        return params;
    }

    /**
     * url地址中文处理
     * @param url
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String encodeChineseInUrl(String url) throws UnsupportedEncodingException {
        StringBuilder encodedUrl = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                encodedUrl.append(URLEncoder.encode(String.valueOf(c), "UTF-8"));
            } else {
                encodedUrl.append(c);
            }
        }
        return encodedUrl.toString();
    }
}
