package Middleware.CausalOrder;

import java.util.List;
import java.util.ArrayList;

public class VectorMessage implements VectorOrdering {

    private int id;
    private List<Integer> v;
    private Object content;
    //para a recovery
    private String operation;

    public VectorMessage(){
        this.id = -1;
        this.v = new ArrayList<>();
    }

    public VectorMessage(int id, List<Integer> v, Object content, String operation){
        this.id = id;
        this.v = v;
        this.content = content;
        this.operation = operation;
    }

    public Object getContent(){
        return content;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void setIndex(int index, int value) {
        this.v.set(index,value);
    }

    public void setVector(List<Integer> v) {
        this.v = v;
    }

    public List<Integer> getVector(){
        return this.v;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public int getIndex(int index) {
        return v.get(index);
    }

    @Override
    public String toString() {
        String content = this.content == null ? "null" : this.content.toString();
        StringBuilder strb = new StringBuilder();
        for(Integer i : v){
            strb.append(Integer.toString(i)).append('/');
        }
        return "VectorMessage{ " +
                " serverId= " + this.id +
                " v= " + strb.toString() +
                " content= " + content +
                '}';
    }
}
