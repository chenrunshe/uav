// DataInterface.aidl
package com.example.lanyatongxin.aidl;

// Declare any non-default types here with import statements

interface DataInterface {

    int getId();

    void sendData(in byte[] data);

    byte[] getData();

}