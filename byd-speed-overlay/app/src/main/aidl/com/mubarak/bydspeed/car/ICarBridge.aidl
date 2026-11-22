package com.mubarak.bydspeed.car;

interface ICarBridge {
    void destroy() = 16777114;
    String invokeGet(String className, in String[] methods, in int[] args) = 1;
    String invokeSet(String className, String method, in double[] args) = 2;
    String scanReport() = 3;
}
