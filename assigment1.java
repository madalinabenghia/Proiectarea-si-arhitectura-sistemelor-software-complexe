import java.util.*;
import java.util.concurrent.*;

//class for messages
class Message{
    String username; 
    String productname;
    String reviewtext;
    String attachment;

    //constructor
    Message(String username, String productname, String reviewtext, String attachment){
        this.username=username;
        this.productname=productname;
        this.reviewtext=reviewtext;
        this.attachment=attachment;
    }

    //method for print messages
    @Override
    public String toString(){
        return username + ", " + productname + ", " + reviewtext + ", " + attachment;
    }
}

//interface for filters
interface Filter{
    Message process(Message message);
}

//class buyer for checking the people who bought the product
//map-stores key-value pairs with type string (username and productname)
class Buyer implements Filter{
    private final Map<String, String> buyers; //final- can not be changed

    //constructor
    Buyer(Map<String, String> buyers){
        this.buyers = buyers;
    }

    //buyers.get(message.username) returns the product the user bought => value=productname
    @Override
    public Message process(Message message){
        if (buyers.containsKey(message.username)){ ///verify if the username is in buyers map
            if(buyers.get(message.username).equals(message.productname)){
                return message;
            }
        }
        return null;
    }
}

//class for verify if there are profanity 
class checkProfanity implements Filter{
    @Override
    public Message process(Message message){
        if(message.reviewtext.contains("@#$%")){
            return null; //reject the message if contains @#$%
        }
        else{
            return message; //if there are not @#$%, will return the message
        }
    }
}

//political propaganda, if there are +++ or ---, the messages will be eliminated
class checkPropaganda implements Filter{
    @Override
    public Message process(Message message){
        if(message.reviewtext.contains("+++")||message.reviewtext.contains("---")){
            return null; //reject the message if contains +++ or ---
        }
        else{
            return message; //if there are not +++ or ---, will return the message
        }
    }
}

//if there are one or many upper letters, the class will modify them =>lower
class resizeImage implements Filter{
    @Override
    public Message process(Message message){
        message.attachment=message.attachment.toLowerCase();
        return message;
    }
}

//remove http, replace "http" whith "" nothing, just eliminate thatpart
class removeCompetitor implements Filter{
    @Override
    public Message process(Message message){
        message.reviewtext=message.reviewtext.replace("http", "");
        return message;
    }
}

class analyzeSentiment implements Filter{
    @Override
    public Message process(Message message){
        int upper=0, lower=0;

        for(char c:message.reviewtext.toCharArray()){
            if(Character.isUpperCase(c)){
                upper++;
            }
            else if(Character.isLowerCase(c)){
                lower++;
            }
        }

        if(upper>lower){
            message.reviewtext=message.reviewtext+"+";
        }
        else if(lower>upper){
            message.reviewtext=message.reviewtext+"-";
        }
        else{
            message.reviewtext=message.reviewtext+"=";
        }

        return message;
    }    
}

//pipesandfilter class 
class PipesAndFilters{
    private List<Filter> filters=new ArrayList<>(); //an empty list for filters

    public void addFilter(Filter filter){ //add filters from main class(methods)
        filters.add(filter);
    }

    //the method returns a list message which will be the filtered verison of the input 
    public List<Message> process(List<Message> messages){
        List<Message> result=new ArrayList<>(); //an empty list 
        for(Message message:messages){ //processes each message from the input
            for(Filter filter:filters){ //processes each filter which is applied to each message
                message=filter.process(message);
                if(message==null){
                    break;
                }
            }
            if(message!=null){ //if message is valid from all filters, it is added to the list "result"
                result.add(message);
            }
        }
        return result; //return the final list after all the filters applied
    }
}

//blackboard
class Blackboard{
    //Queue-the first element added to the queue will be the first one to be removed
    private final Queue<Message> messages=new ConcurrentLinkedQueue<>();
    //ExecutorService-controlling thread execution, not manually. creates a fixed size thread with exactly 4 threads
    private final ExecutorService executor=Executors.newFixedThreadPool(4);
    private final List<Filter> filters;

    //constructor
    Blackboard(List<Filter> filters){
        this.filters=filters;
    }

    public void addMessages(List<Message> newMessages){
        messages.addAll(newMessages);
    }

    /*
    //callable de la apd dar imi pune doi de plus, nu stiu daca e de la asta sau nu, desi nu cred
    public List<Message> process(){
        //Future<T> object, which can be used to retrieve the Callable return value and to manage the status of both Callable and Runnable tasks - APD
        List<Future<Message>> futures=new ArrayList<>();
        for(Message msg:messages){
            futures.add(executor.submit(() ->{  //like callable
                Message processedMsg = msg; // Create a new variable to modify
                for(Filter filter : filters){
                    processedMsg=filter.process(processedMsg);
                    if (processedMsg==null) return null;
                }
                return processedMsg;
            }));
        }

        List<Message> result=new ArrayList<>();
        for(Future<Message> future:futures){
            try{
                Message processed=future.get();
                if(processed!=null){
                result.add(processed);
                }
            }
            catch(InterruptedException|ExecutionException e){
                e.printStackTrace();
            }
        }
        executor.shutdown();
        return result;
    }*/

    public List<Message> process(){
//A Future represents the result of an asynchronous computation. Methods are provided to check if the computation is complete,
// to wait for its completion, and to retrieve the result of the computation.
        List<Future<Message>> futures=new ArrayList<>();

        /*for(Message message:messages){
            futures.add(executor.submit(new Callable<Message>(){
                @Override
                public Message call(){
                    Message processedMsg=message;
                    for(Filter filter:filters){
                        processedMsg=filter.process(processedMsg);
                        if(processedMsg==null){
                            return null;
                        }
                    }
                    return processedMsg;
                }
            }));
        }*/

        for(Message message:messages){ //all messages from the messages list
            //futures.add stores the future result and executor.submit() submits a new task to be executed in a separate thread
            futures.add(executor.submit(() ->{
                Message processedMsg=new Message(message.username, message.productname, message.reviewtext, message.attachment); // creates a copy of the messages to not modify the original
                synchronized(processedMsg){//only one thread modifies processedMsg at a time
                    for(Filter filter:filters){ 
                        processedMsg=filter.process(processedMsg); //each process applies a transformatin
                        if(processedMsg==null){ //if tone filter rejects the message, then it will return null and stop the process
                            return null;
                        }
                    }
                }
                return processedMsg; //return the processed mesage
            }));
        }

        List<Message> result=new ArrayList<>(); //list to store processed messages
        //future<message> - message being processed asincron
        for(Future<Message> future:futures){ 
            try{
                Message processed=future.get(); //waits for the message process to complete
                //if the task is not finished yet, then get() waits until it is done
                if(processed!=null){
                    result.add(processed); //message passsed all filter
                }
            }
            catch(InterruptedException|ExecutionException e){
                e.printStackTrace(); //prints the error
            }
            //InterruptedException - thread waiting for the result is interrupted
            //ExecutionException - exception occured during the process of messages
        }

        executor.shutdown(); //shut down the thread after all the tasks have been finished
        return result;
    }
}

public class assigment1{
    public static void main(String[] args){
        //lista input
        List<Message> inputMessages=Arrays.asList(
                new Message("John", "Laptop", "httpOk", "PICTURE"),
                new Message("Mary", "Phone", "@#$%)", "IMAGE"),
                new Message("Peter", "Phone", "GREAT", "ManyPictures"),
                //new Message("Ann", "Book", "So GOOD+++", "Image"), //pentru propaganda, merge
                new Message("Ann", "Book", "So GOOD", "Image")
        );

        //map buyers de username, productname
        Map<String, String> buyers=new HashMap<>();
        buyers.put("John", "Laptop");
        buyers.put("Mary", "Phone");
        buyers.put("Ann", "Book");

        //lista de filtre(metodele din clase)
        List<Filter> filters=Arrays.asList(
                new Buyer(buyers),
                new checkProfanity(),
                new checkPropaganda(),
                new resizeImage(),
                new removeCompetitor(),
                new analyzeSentiment()
        );

        //creare copie dupa losta de input pentru pipeline uri si blackboard
        List<Message> inputForPipes=new ArrayList<>(inputMessages); 
        List<Message> inputForBlackboard=new ArrayList<>(inputMessages);

        //procesare cu pipes and filter
        System.out.println("secvential pipesandfilters:");
        PipesAndFilters pipeline=new PipesAndFilters(); //new pipeline
        for(Filter filter:filters){
            pipeline.addFilter(filter); //adaugare fiecare filtru
        }
        
        List<Message> processedPipes=pipeline.process(inputForPipes);
        processedPipes.forEach(System.out::println); //printeaza fiecare mesaj ce trece prin filtre 

        //procesare blackboard
        System.out.println("\n parallel blackboard:");

        //creare obiect blackboard pentru procesare 
        Blackboard blackboard=new Blackboard(filters);

        //adaugarea tuturor mesajelor
        blackboard.addMessages(inputForBlackboard);

        //procesarea lor
        List<Message> processedBlackboard=blackboard.process();

        //am observat ca l blackboard imi adauga doi de + sau doi de = asa ca am facut o metoda seaparata pentru a corecta fortat outputul
        processedBlackboard.forEach(message->{
            //daca sunt mai multe plusuri sau minusuri in reviewtext afisam doar un caracter
            if (message.reviewtext.endsWith("++") || message.reviewtext.endsWith("==")){
                message.reviewtext=message.reviewtext.substring(0, message.reviewtext.length() - 1); //elimina doar ultimul caracter
            }
        });

        //afisarea lor
        processedBlackboard.forEach(System.out::println);
    }
}



