# The AWK Programming Language


## CONTENTS
- [AN AWK TUTORIAL](#an-awk-tutorial)
- [THE AWK LANGUAGE](#the-awk-language)
- [DATA PROCESSING](#data-processing)


## AN AWK TUTORIAL

### Getting Started

```
awk '$3 > 0 { print $1, $2 * $3 }' emp.data
awk '$3 == 0 { print $1 }' emp.data

awk 'program' input files

awk '$3 == 0 { print $1 }' file1 file2

# end-of-file signal (control-d on Unix systems)
awk 'program '

awk '$3 == 0 { print $1 }'

awk -f progfile optional list ofinput files
```
- Since patterns and actions are both optional, actions are enclosed in braces to distinguish them from patterns.

### Simple Output

There are only two types of data in awk: numbers and strings of characters.
```
{ print }
{ print $0 }
{ print $1, $3 }
{ print NF, $1, $NF }
{ print $1, $2 * $3 }
{ print NR, $0 }
{ print "total pay for", $1, "is", $2 * $3 }
```

### Fancier Output

```
printf(format, value1, value2, ..., valueN)
{ printf("total pay for %s is $%.2f\n", $1, $2 * $3) }
{ printf("%-8s $%6.2f\n", $1, $2 * $3) }
awk '{ printf("%6.2f %s\n", $2 * $3, $0) }' emp.data | sort
```

### Selection

```
$2 >= 5
$2 * $3 > 50 { printf("$%.2f for %s\n", $2 * $3, $1) }
$1 == "Susie"
/Susie/
$2 >= 4 || $3 >= 20

$2 >= 4 
$3 >= 20

!($2 < 4 && $3 < 20)

NF != 3   { print $0, "number of fields is not equal to 3" }
$2 < 3.35 { print $0, "rate is below minimum wage" }
$2 > 10   { print $0, "rate exceeds $10 per hour" }
$3 < 0    { print $0, "negative hours worked" }
$3 > 60   { print $0, "too many hours worked" }

BEGIN { print "NAME RATE HOURS"; print "" }
      { print }
```

### Computing with AWK

+ Awk variables used as numbers begin life with the value 0, so we didn't need to initialize emp.
+ Variables used to store strings begin life holding the null string (that is, the string containing no characters), so in this program names did not need to be explicitly initialized.
```
$3 > 15 { emp = emp + 1 }
END     { print emp, "employees worked more than 15 hours" }

END { print NR, "employees" }

    { pay =pay + $2 * $3 }
END { print NR, "employees"
	  print "total pay is", pay 
	  print "average pay is", pay/NR
	}

$2 > maxrate { maxrate = $2; maxemp = $1 }
END { print "highest hourly rate:", maxrate, "for", maxemp }

    { names = names $1 " " }
END { print names }

    { last = $0 }
END { print last }

{ print $1, length($1) }

	{ nc = nc + length($0) + 1 
	  nw = nw + NF
	}
END { print NR, "lines,", nw, "words,", nc, "characters" }
```

### Control-Flow Statements

```
$2 > 6 { n = n + 1; pay = pay + $2 * $3 }
END    { if (n > 0)
			 print n, "employees, total pay is ", pay,
					  "average pay is", pay/n
		 else
			 print "no employees are paid more than $6/hour"
	   }

# interest1 - compute compound interest
# input: amount rate years
# output: compounded value at the end of each year
{	i = 1
	while (i <= $3) {
		printf("\t%.2f\n", $1 * (1 + $2) ^ i) 
		i = i + 1
	}
}

awk -f interest1

# interest2 - compute compound interest
# input: amount rate years
# output: compounded value at the end of each year
{	for (i = 1; i <= $3; i = i + 1) 
		printf("\t%.2f\n", $1 * (1 + $2) ^ i)
}
```

### Arrays


```
# reverse - print input in reverse order by line
    { line[NR] = $0 } # remember each input line
END { i = NR		  # print lines in reverse order
		while (i > 0) {
			print line[i]
			i = i - 1
		}
	}

# reverse - print input in reverse order by line
    { line[NR] = $0 } # remember each input line
END { for (i = NR; i > 0; i = i - 1)
		print line[i]
	}
```

### A Handful of Useful "One-liners"

```
END { print NR }

NR == 10

{ print $NF }

    { field = $NF}
END { print field }

NF > 4

$NF > 4

    { nf = nf + NF }
END { print nf }

/Beth/ { nlines = nlines + 1 }
END    { print nlines }

$1 > max { max = $1; maxline = $0}
END      { print max, maxline }

NF > 0

length($0) > 80

{ print NF, $0 }

{ print $2, $1 }

{ temp = $1; $1 = $2; $2 = temp; print }

{ $1 = NR; print }

{ $2 = ""; print }

{	for (i = NF; i > 0; i = i - 1) printf("%s " $i) 
	printf("\n" )
}

{	sum = 0
	for (i = 1; i <= NF; i = i + 1) sum = sum + $i 
	print sum
}

    { for (i = 1; i <= NF; i = i + 1) sum = sum + $i }
END { print sum }

{ for (i = 1; i <= NF; i = i + 1) if ($i < 0) $i = -$i 
  print
}
```

## THE AWK LANGUAGE
## DATA PROCESSING
