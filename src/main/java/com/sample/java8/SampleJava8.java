package com.sample.java8;

import java.util.Arrays;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author 1388162
 */
public class SampleJava8 {

    public static void main(String[] args) {

        Trader raoul = new Trader("Raoul", "Cambridge");
        Trader mario = new Trader("Mario","Milan");
        Trader alan = new Trader("Alan","Cambridge");
        Trader brian = new Trader("Brian","Cambridge");
        List<Transaction> transactions = Arrays.asList(
                new Transaction(brian, 2011, 300),
                new Transaction(raoul, 2012, 1000),
                new Transaction(raoul, 2011, 400),
                new Transaction(mario, 2012, 710),
                new Transaction(mario, 2012, 700),
                new Transaction(alan, 2012, 950)
                                                      );
        //Find all transactions in 2011 and sort by value (small to high)
        transactions.stream()
                    .filter(x -> x.getYear() == 2011)
                    .sorted(comparing(Transaction::getValue))
                    .collect(toList());

        //What are all the unique cities where the traders work?
        transactions.stream()
                    .map(x -> x.getTrader().getCity())
                    .distinct()
                    .forEach(System.out::println);

        //Find all traders from Cambridge and sort them by name
        transactions.stream()
                    .map(Transaction::getTrader)
                    .filter(x -> x.getCity().equals("Cambridge"))
                    .sorted(comparing(Trader::getName))
                    .forEach(System.out::println);

        //Return a string of all traders’ names sorted alphabetically
        System.out.println(transactions.stream()
                    .map(x -> x.getTrader().getName())
                    .distinct()
                    .sorted()
                    .collect(joining())
                    );

        //Are any traders based in Milan?
        System.out.println(transactions.stream()
                    .anyMatch(x -> x.getTrader().getCity().equals("Milan"))
                          );

        //Print all transactions values from the traders living in Cambridge
        transactions.stream()
                    .filter(x -> x.getTrader().getCity().equals("Cambridge"))
                    .map(Transaction::getValue)
                    .forEach(System.out::println);

        //What’s the highest value of all the transactions?
        System.out.println(transactions.stream()
                    .map(Transaction::getValue)
                    .max(Integer::compareTo)
                    .get()
                          );

    }
}
