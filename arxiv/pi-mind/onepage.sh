sed -n -e "0,/title/ p" $1
echo "<style>"
cat pi-mind.css
echo "</style>"
echo "<script type='text/javascript'>"
cat pi-mind.js
echo "</script>"
echo "</head>"
sed -n -e "0,/\/head/! p" $1
