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
ID : [a-zA-Z][a-zA-Z0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

// ----------------
// - PARSER RULES -
// ----------------
program
    : importDecl* classDecl EOF
    ;

classDecl
    : CLASS name=ID classExtends?
        LCURLY
        (varDecl | methodDecl | mainMethodDecl)*
        RCURLY #ClassDeclaration
    ;

// name variable not capturing full import path
importDecl
    : IMPORT name=ID (DOT ID)* SEMI #ImportDeclaration
    ;

classExtends
    : EXTENDS name=ID #ExtendClass
    ;

varDecl
    : param SEMI #VariableDeclaration
    ;

type
    : INT #IntegerType
    | BOOLEAN #BooleanType
    | STRING #StringType
    | ID #OtherType
    ;

dottedStrings
    : ID (DOT ID)*
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param (COMMA param)* RPAREN
        LCURLY varDecl* stmt* RCURLY #MethodDeclaration
    ;

mainMethodDecl
    : STATIC VOID 'main' LPAREN STRING LRECT RRECT 'args' RPAREN LCURLY stmt* RCURLY #MainMethodDeclaration
    ;

param
    : type (LRECT RRECT | '...')? name=ID #Parameter
    ;

stmt
    : LCURLY stmt* RCURLY #BlockStatement
    | IF LPAREN expr RPAREN stmt (ELSE stmt)? #IfStatement
    | WHILE LPAREN expr RPAREN stmt #WhileStatement
    | expr EQUALS expr SEMI #AssignmentStatement
    | RETURN expr SEMI #ReturnStatement
    | name=ID SEMI #ExpressionStatement
    ;

expr
    : expr LRECT expr RRECT #ArrayAccess
    | expr DOT ID #PropertyAccess
    | expr DOT LENGTH #LengthAccess
    | expr LPAREN exprList? RPAREN #MethodCall
    | value=(TRUE | FALSE) #BooleanValue
    | THIS #This
    | name=ID #Variable
    | value=INTEGER #IntegerLiteral
    | LPAREN expr RPAREN #ParenthesesExpression
    | expr op=(MUL | DIVISION) expr #BinaryOp
    | expr op=(ADD | SUB) expr #BinaryOp
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
