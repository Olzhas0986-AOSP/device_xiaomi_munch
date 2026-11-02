package vendor.xiaomi.hardware.touchfeature.V1_0;
import android.os.RemoteException;
public class ITouchFeature {
 public static ITouchFeature service;
 public int calls; public int failAt=-1;
 public final java.util.Map<Integer,Integer> modes=new java.util.HashMap<>();
 public static ITouchFeature getService() throws RemoteException {
  if(service==null) throw new java.util.NoSuchElementException();
  return service;
 }
 public void setTouchMode(int mode,int value) throws RemoteException { if(calls++==failAt) throw new RemoteException(); modes.put(mode,value); }
 public void resetTouchMode(int mode) throws RemoteException { if(calls++==failAt) throw new RemoteException(); modes.remove(mode); }
}
