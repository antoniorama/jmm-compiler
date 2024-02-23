grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
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
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;

INTEGER : [0-9]+ ;
ID : [a-zA-Z]+ ;
DOT : '.' ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;


classDecl
    : CLASS name=ID classExtends?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

importDecl
    : IMPORT name=dottedStrings SEMI
    ;

classExtends
    : EXTENDS name=ID
    ;

varDecl
    : type (LRECT RRECT)? name=ID SEMI
    ;

type
    : INT
    | BOOLEAN
    | ID
    ;

dottedStrings
    : ID (DOT ID)*
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : expr op= MUL expr #BinaryExpr //
    | expr op= ADD expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;



