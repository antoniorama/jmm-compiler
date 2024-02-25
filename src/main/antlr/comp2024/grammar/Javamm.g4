grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
COMMA : ',' ;
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LRECT : '[';
RRECT : ']';
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
DIVISION : '/' ;
ADD : '+' ;
SUB : '-';
DOT : '.' ;
NOT : '!' ;

CLASS : 'class' ;
INT : 'int' ;
BOOLEAN : 'boolean';
VOID : 'void' ;
STRING : 'String' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
STATIC : 'static' ;
LENGTH : 'length' ;
NEW : 'new' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

INTEGER : [0-9]+ ;
ID : [a-zA-Z][a-zA-Z0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

classDecl
    : CLASS name=ID classExtends?
        LCURLY
        (varDecl | methodDecl | mainMethodDecl)*
        RCURLY
    ;

importDecl
    : IMPORT name=dottedStrings SEMI
    ;

classExtends
    : EXTENDS name=ID
    ;

varDecl
    : param SEMI
    ;

type
    : INT
    | BOOLEAN
    | STRING
    | ID
    ;

dottedStrings
    : ID (DOT ID)*
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param (COMMA param)* RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

mainMethodDecl
    : STATIC VOID 'main' LPAREN STRING LRECT RRECT 'args' RPAREN LCURLY stmt* RCURLY
    ;

param
    : type (LRECT RRECT | '...')? name=ID
    ;

stmt
    : block
    | ifStmt
    | whileStmt
    | expr EQUALS expr SEMI
    | RETURN expr SEMI
    | ID SEMI
    ;

block
    : LCURLY stmt* RCURLY
    ;

ifStmt
    : IF LPAREN expr RPAREN stmt (ELSE stmt)?
    ;

whileStmt
    : WHILE LPAREN expr RPAREN stmt
    ;

expr
    : expr LRECT expr RRECT #ArrayAccess
    | expr DOT ID #PropertyAccess
    | expr DOT LENGTH #LengthAccess
    | expr LPAREN exprList? RPAREN #MethodCall
    | ID #Variable
    | INTEGER #IntegerLiteral
    | LPAREN expr RPAREN #ParenExpr
    | expr (MUL | DIVISION) expr #BinaryOp
    | expr (ADD | SUB) expr #BinaryOp
    | NOT expr #NotExpr
    | NEW type LRECT expr RRECT #NewArray
    | NEW ID LPAREN exprList? RPAREN #NewClassInstance
    ;

exprList
    : expr (COMMA expr)*
    ;
