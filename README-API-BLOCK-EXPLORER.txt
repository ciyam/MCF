Special instructions for API / block-explorer:

If you're reading this then you have checked out the correct branch!

You should see a "db" folder and "MCF-core.jar" in current folder.
The "db" folder should contain approx. 480MB over ~4 files.
MCF-core.jar is about 37MB with MD5 of 8033a40a849065d80e67dcc195e1c905

Fire up the API server:

java -jar MCF-core.jar

You can now access the API online documentation via:

http://localhost:9085/api-documentation/

To access the block explorer, open a new tab in
your browser then open the file

block-explorer.html

This stand-alone HTML file will make calls to the API
via localhost.
