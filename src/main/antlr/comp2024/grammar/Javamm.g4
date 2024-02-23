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
ADD : '+' ;

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

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

INTEGER : [0-9]+ ;
ID : [a-zA-Z] [a-zA-Z0-9]* ;
DOT : '.' ;

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

block
    : LCURLY stmt* RCURLY
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt
    | RETURN expr SEMI #ReturnStmt
    | ID SEMI #SimpleStmt
    | block #BlockStmt
    | ifStmt #IfElseStmt
    | whileStmt #WhileLoopStmt
    ;

ifStmt
    : IF LPAREN expr RPAREN stmt* (ELSE block)?
    ;

whileStmt
    : WHILE LPAREN expr RPAREN stmt*
    ;

expr
    : expr op=MUL expr #BinaryExpr
    | expr op=ADD expr #BinaryExpr
    | LPAREN expr RPAREN #ParenExpr
    | value=INTEGER #IntegerLiteral
    | name=ID #VarRefExpr
    | ID LRECT expr RRECT #ArrayAccessExpr
    ;




