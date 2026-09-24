package dihclient.util.multi;

public interface MultiConnectionMarker {
    boolean dih$isMultiManaged();

    MultiConnectionContext.ProxySpec dih$multiProxy();

    void dih$setMultiManaged(MultiConnectionContext.ProxySpec proxy);

    void dih$clearMultiManaged();
}
