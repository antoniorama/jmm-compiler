grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

// ---------------
// - LEXER RULES -
// ---------------

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
LESS : '<' ;
GREATER : '>' ;
AND : '&&' ;
OR : '||' ;
SINGLE_COMMENT : '//' .*? '\n' -> skip ;
MULTI_COMMENT :  '/*' .*? '*/'  '\n' -> skip ;

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
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z0-9_]* ;

WS : [ \t\n\r\f]+ -> skip ;

// ----------------
// - PARSER RULES -
// ----------------
program
    : importDecl* classDecl EOF
    ;

classDecl
    : CLASS name=ID (EXTENDS extendedClass=ID)?
        LCURLY
        (varDecl | methodDecl | mainMethodDecl)*
        RCURLY
    ;

// name variable not capturing full import path
importDecl
    : IMPORT name=ID (DOT ID)* SEMI
    ;

varDecl
    : param SEMI
    ;

type
    : type LRECT RRECT #ArrayType
    | value=INT #IntegerType
    | value=BOOLEAN #BooleanType
    | value=STRING #StringType
    | value=VOID #VoidType
    | name=ID #OtherType
    ;

dottedStrings
    : ID (DOT ID)*
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param (COMMA param)*)? RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

mainMethodDecl
    : PUBLIC? STATIC VOID name='main' LPAREN STRING LRECT RRECT 'args' RPAREN LCURLY stmt* RCURLY
    ;

param
    : type (LRECT RRECT | '...')? name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #BlockStmt
    | IF LPAREN expr RPAREN stmt (ELSE stmt)? #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr EQUALS expr SEMI #AssignStmt
    | RETURN expr SEMI #ReturnStmt
    | name=ID SEMI #ExpressionStmt
    ;

expr
    : expr LRECT expr RRECT #ArrayAccess
    | expr DOT ID #PropertyAccess
    | expr DOT LENGTH #LengthAccess
    | expr LPAREN exprList? RPAREN #MethodCall
    | value=(TRUE | FALSE) #BooleanValue
    | THIS #This
    | name=ID #VarRefExpr
    | value=INTEGER #IntegerLiteral
    | LPAREN expr RPAREN #ParenthesesExpression
    | expr op=(MUL | DIVISION) expr #BinaryOp
    | expr op=(ADD | SUB) expr #BinaryExpr
    | expr op=(GREATER | LESS) expr #RelationalExpression
    | expr op=(AND | OR) expr #LogicalExpression
    | NOT expr #NotExpression
    | NEW type LRECT expr RRECT #NewArray
    | LRECT exprList? RRECT #ArrayInit
    | NEW className=ID LPAREN exprList? RPAREN #NewClassInstance
    ;

exprList
    : expr (COMMA expr)* #ExpressionList
    ;
