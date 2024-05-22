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
AND : '&&' ;
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
NEW : 'new' ;
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

NULL : 'null' ;

INTEGER : '0' | [1-9][0-9]* ; // changed so there arent any leading zeros
ID : [a-zA-Z_$]([a-zA-Z0-9_$])* ;

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

importDecl
    : IMPORT name+=ID (DOT name+=ID)* SEMI
    ;

varDecl
    : param SEMI
    ;

type
    : type LRECT RRECT #ArrayType
    | type '...' #VarArgsType
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
    : PUBLIC? STATIC VOID name=ID LPAREN STRING LRECT RRECT args=ID RPAREN LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #BlockStmt
    | IF LPAREN expr RPAREN stmt (ELSE IF LPAREN expr RPAREN stmt)* ELSE stmt #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr EQUALS expr SEMI #AssignStmt
    | RETURN expr SEMI #ReturnStmt
    | expr SEMI #ExpressionStmt
    ;

expr
    : expr LRECT expr RRECT #ArrayAccess
    | expr DOT name=ID #PropertyAccess
    | methodName=ID LPAREN exprList? RPAREN #MethodCallOnAssign
    | expr '.' methodName=ID LPAREN (expr (COMMA expr) *)? RPAREN #MethodCall
    | value=(TRUE | FALSE) #BooleanValue
    | THIS #This
    | name=ID #VarRefExpr
    | value=INTEGER #IntegerLiteral
    | LPAREN expr RPAREN #ParenthesesExpression
    | expr op=(MUL | DIVISION) expr #BinaryExpr
    | expr op=(ADD | SUB) expr #BinaryExpr
    | expr op=LESS expr #RelationalExpression
    | expr op=AND expr #LogicalExpression
    | NOT expr #NotExpression
    | NEW type LRECT expr RRECT #NewArray
    | LRECT exprList? RRECT #ArrayInit
    | NEW name=ID LPAREN exprList? RPAREN #NewClassInstance
    ;

exprList
    : expr (COMMA expr)* #ExpressionList
    ;
