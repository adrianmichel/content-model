[TOC]

#Java XML Content Model Validator

## What is this repository for? ##

This component was originally written as a content model validator for a XML Schema parser.

It is conceptually similar to a regular expression matcher, with a few specific features such as accepting an empty element or requiring a deterministic finite state machine.

## Setup ##

### Building and running the project ###

The maven project file pom.xml can be opened in Eclipse for example for compilation and testing, the only requirement being a recent jvm (1.8 or higher).

It is a standalone component and doesn't require any configuration.

### Dependencies ###

It depends on the "Apache Lang 3" library (set as dependency in in the pom.xml file) from which it uses the Validate and Pair classes.

### Testing ###

The project comes with a unit-test class "com.amichel.contentmodel.Test" which ensures the library is functional and also illustrates its use.

The tests will print out detailed information about the content model run internals, such as syntax tree and state machine structure.

## Credits ##

The implementation of this module is based on a couple of papers: [Unambiguity of Extended Regular Expressions in SGML Grammars](http://www.amichel.com/xmlcontentmodel/10.1.1.33.8918.pdf) and especially [Regular Expressions into Finite Automata](http://www.amichel.com/xmlcontentmodel/10.1.1.33.9232.pdf) both written by Anne Brüggemann-Klein.

## Introduction ##

The XML Content Model Validator (CMV) is a standalone module I wrote several years ago as part of a larger Java based XML framework.

Compared to the original project, I updated the current version to use modern Java constructs (generics, streams, forEach etc) and cleaned up the code, but I haven't changed the overall structure or logic. This implementation is based on the language features available when it was originally written, and if I had to write it today from scratch, I would probably implement parts of it differently.

## General concepts ##
For those new to some of these concepts, here are the very basic facts - DTDs (Document Type Definition) and XML Schemas (XSD) define a number of constraints that an XML or XSD document must follow to be considered valid relative to that DTD or Schema.

An XML or XSD document has a hierarchical structure formed of elements, and each element may contain sub-elements, as well as have a number of attributes. The content model is a description of the valid structure of these elements - what and how many sub-elements or attributes they can have, and in which order these sub-elements must appear (the order is not relevant for attributes). For a detailed description of XML Schema content models see [[http://www.w3.org/TR/xmlschema-0/]] and [[http://www.w3.org/TR/xmlschema-1/]]. What's relevant in this context though is that content models are similar in concept and can be described in a regular expression like language. In fact "content model" and "regular expression" will be used interchangeably throughout this document.

The content model regular expressions have specific characteristics compared to ordinary regular expressions. For example, they must be deterministic (see below for a short explanation of what that means). Also, they do not need all the features that a general purpose regular expression usually provide. This module was developed with these factors in mind, but it could be extended to support more features if needed.

The CMV was written in Java but could be easily ported to other languages, due to its modular design.

## Structure ##

The module contains the following main logical components:

* an expression parser (accepts input in both conventional and Reverse Polish Notation format)
* a syntax tree builder
* syntax tree
* state machine compiler
* state machine

Conceptually, this is how it works.

The two inputs are the content model (regular expression) and the actual instance structure that needs to be matched against the content model.

1. the regular expression is passed to the CMV
1. the CMV parses it and generates a syntax tree which is a hierarchical representation of the regular expression, with nodes being operators and leaves being operands.
1. the syntax tree is compiled into a deterministic finite state machine (or deterministic finite automaton - DFA).
1. the string representing the instance structure as passed to the state machine which is then run. This step will return a true/false result indicating a match or mismatch between the initial regular expression and instance structure. In the case of a "no match" result, more information can be extracted from the state machine to determine what exactly caused the failure.

If the regular expression is complex, the state machine generation can take longer, but a state machine has to be created just once and can then be cached or even serialized (feature not implemented) for faster processing later on.

The validation part is very efficient, as generally all state machine based algorithms are.

## Usage ##

Integrating the module with other projects is relatively simple - see the Test class for a detailed example.

To use the module, the content model must be represented by an expression containing several operators:

```
#!

| - OR
, - AND
? - 0 or 1 occurrence
* - 0 or more occurrences
+ - 1 or more occurrences
[m,n] - between m and n occurrences
<empty> - not an operator, used to represent the empty element
() - grouping of subexpressions

```

Note: general purpose regular expression don't generally have an explicit operator AND - simple concatenation achieves the same result. However, in this case concatenation is used to represent whole words, and the "," (comma) operator is used as AND operator. Words in the instance string (the one that must be validated) must be separated by "," (comma).

A few examples of valid expressions that can be used to describe unambiguous content models


```
#!

apple?
(apple|pear)+
apple?,pear
apple?,pear?,cherry?
(apple),(apple*,pear)*
(apple,pear)*|(melon,banana)|(cherry,peach)
(apple[2,3],pear[2,3])[5,6]
((apple?,pear?,cherry)|(mellon*,banana?)+|peach|(apricot,lettuce) - just making sure you're paying attention

```
and these are examples of ambiguous regular expressions which will trigger runtime exceptions:


```
#!

a*|(a,b) 
(a,b)|(a,c) 
(a,b)*|(a,c) 
a,(b,a)*,(b|<empty>) 
a|(a,b) 
(a|b)*,a,b,b
```

## Ambiguous vs. unambiguous regular expressions ##

By design XML only accepts unambiguous content models, and this implementation will also accept only unambiguous regular expressions as input. An exception will be thrown if a content model is ambiguous. An ambiguous regular expression is detected during state machine compilation phase when at lest one state has 2 ore more exit transitions triggered by the same input symbol (or word). In that case it is obviously not possible to determine the valid transition (without looking ahead in the expression).

General purpose regular expressions accept ambiguous expressions as they usually look ahead in case of ambiguity to determine the transition that matches.

## Feedback ##

Email [info@amichel.com](mailto:info@amichel.com) if you have any feedback or questions.
