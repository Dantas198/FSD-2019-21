package Middleware.TwoPhaseCommit;

public class TransactionMessage<V>  {
    private int senderId;
    private int transactionId;
    private char type;
    private int phase;
    private V content;

    public TransactionMessage(int senderId, V content){
        this.senderId = senderId;
        this.transactionId = -1;
        this.type = 'b';
        this.phase = 0;
        this.content = content;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public void setCommited(){
        this.type = 'c';
    }

    public void setAborted(){
        this.type = 'a';
    }

    public void setPrepared(){
        this.type = 'p';
    }

    public void setFinished(){this.type = 'f';}

    public boolean notStarted(){
        return type == 'b';
    }

    public boolean isAborted(){
        return type == 'a';
    }

    public  boolean isPrepared(){
        return  type == 'p';
    }

    public boolean isCommited(){
        return type == 'c';
    }

    public boolean isFinished() {return type == 'f';}

    public V getContent() {
        return content;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public void setContent(V content) {
        this.content = content;
    }

    public boolean isSecondPhase(){return phase == 2;}

    public boolean isFirstPhase(){return phase == 1;}


    public void setPhase(int phase) {
        this.phase = phase;
    }



    @Override
    public String toString() {
        return "TransactionMessage{" +
                "senderId=" + senderId +
                ", transactionId=" + transactionId +
                ", content= " + content.toString() +
                ", type=" + type +
                ", phase=" + phase +
                '}';
    }
}
